package com.gimnasio.sistemagym;

import com.gimnasio.sistemagym.service.PagoService;
import com.gimnasio.sistemagym.service.CustomUserDetailsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
		"spring.datasource.url=jdbc:h2:mem:sistemagym-application-tests;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"sistemagym.membresias.scheduler.enabled=false"
})
class SistemaGymApplicationTests {

	@Autowired
	private DataSource dataSource;

	@Autowired
	private MockMvc mockMvc;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private org.springframework.beans.factory.ObjectProvider<com.gimnasio.sistemagym.service.TareaMembresiasProgramada> tareaProgramada;

	@Autowired
	private org.springframework.context.ApplicationContext applicationContext;

	@Autowired
	private CustomUserDetailsService userDetailsService;

	@Value("${INITIAL_ADMIN_EMAIL}")
	private String correoAdministradorPruebas;

	@Value("${INITIAL_ADMIN_PASSWORD}")
	private String contrasenaAdministradorPruebas;

	@MockitoBean
	private PagoService pagoService;

	@Test
	void contextLoads() throws Exception {
		Assertions.assertNull(tareaProgramada.getIfAvailable(),
				"El scheduler debe permanecer ausente si la bandera está desactivada.");
		try (var connection = dataSource.getConnection()) {
			Assertions.assertTrue(connection.getMetaData().getURL()
					.startsWith("jdbc:h2:mem:sistemagym-application-tests"));
		}
	}

	@Test
	void usaSoloCustomUserDetailsServiceSinProveedorEnMemoria() {
		Assertions.assertArrayEquals(
				new String[]{"customUserDetailsService"},
				applicationContext.getBeanNamesForType(UserDetailsService.class));
		Assertions.assertFalse(applicationContext
				.containsBean("inMemoryUserDetailsManager"));
	}

	@Test
	void getPlanesLeeLaBaseDeDatosYRespondeSinAutenticacion() throws Exception {
		mockMvc.perform(get("/api/planes"))
				.andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
	}

	@Test
	void autenticaAdministradorYAccedeARutaProtegida() throws Exception {
		String[] credentials = credencialesAdministradorDePruebas();
		Assertions.assertEquals("admin@sistemagym.local", credentials[0]);
		Assertions.assertFalse(credentials[1].isBlank());

		mockMvc.perform(get("/api/socios/999999")
					.header("Authorization", bearerToken(credentials)))
				.andExpect(status().isNotFound());
	}

	@Test
	void administradorRealPuedeListarUsuariosSinExponerHash() throws Exception {
		String[] credentials = credencialesAdministradorDePruebas();
		String response = mockMvc.perform(get("/api/usuarios")
					.header("Authorization", bearerToken(credentials)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		Assertions.assertTrue(response.contains("admin@sistemagym.local"));
		Assertions.assertFalse(response.toLowerCase().contains("contrasena"));
		Assertions.assertFalse(response.toLowerCase().contains("hash"));

		Long administratorId = jdbcTemplate.queryForObject(
				"SELECT id_usuario FROM usuarios WHERE correo = ?",
				Long.class, "admin@sistemagym.local");
		String detail = mockMvc.perform(get("/api/usuarios/{id}", administratorId)
					.header("Authorization", bearerToken(credentials)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		Assertions.assertFalse(detail.toLowerCase().contains("contrasena"));
		Assertions.assertFalse(detail.toLowerCase().contains("hash"));
	}

	private String[] credencialesAdministradorDePruebas() {
		return new String[]{correoAdministradorPruebas, contrasenaAdministradorPruebas};
	}

	private String bearerToken(String[] credentials) throws Exception {
		String body = objectMapper.writeValueAsString(java.util.Map.of(
				"correo", credentials[0], "contrasena", credentials[1]));
		String response = mockMvc.perform(post("/api/auth/login")
				.contentType(APPLICATION_JSON).content(body))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return "Bearer " + objectMapper.readTree(response).get("token").asText();
	}

	@Test
	@Transactional(readOnly = true)
	void auditarAdministradorSinLeerSuHash() throws Exception {
		String correo = "admin@sistemagym.local";
		var rows = jdbcTemplate.query(
				"SELECT u.estado, r.nombre AS rol "
						+ "FROM usuarios u JOIN roles r ON r.id_rol = u.id_rol "
						+ "WHERE u.correo = ?",
				(rs, rowNum) -> new String[]{rs.getString("estado"), rs.getString("rol")},
				correo);

		if (rows.isEmpty()) {
			System.out.println("AUDIT_ADMIN|exists=false");
		} else {
			String estado = rows.getFirst()[0];
			String rol = rows.getFirst()[1];
			boolean activo = "ACTIVO".equalsIgnoreCase(estado);
			System.out.println("AUDIT_ADMIN|exists=true|active=" + activo
					+ "|role=" + rol);

			try {
				UserDetails user = userDetailsService.loadUserByUsername(correo);
				boolean hasAdminAuthority = user.getAuthorities().stream()
						.anyMatch(authority -> "ROLE_ADMINISTRADOR".equals(
								authority.getAuthority()));
				System.out.println("AUDIT_ADMIN_LOAD|success|hasAdminAuthority="
						+ hasAdminAuthority);
			} catch (UsernameNotFoundException exception) {
				System.out.println("AUDIT_ADMIN_LOAD|rejected_by_user_details");
			}
		}

		Long zeroIdRows = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM socios WHERE id_socio = 0", Long.class);
		System.out.println("AUDIT_SOCIO_ZERO_ID_EXISTS|"
				+ (zeroIdRows != null && zeroIdRows > 0));
	}

	@Test
	void auditarEsquemaYRolesSinLeerDatosPersonales() throws Exception {
		try (var connection = dataSource.getConnection()) {
			DatabaseMetaData metadata = connection.getMetaData();
			String catalog = connection.getCatalog();
			System.out.println("AUDIT_DB|" + metadata.getDatabaseProductName()
					+ "|" + metadata.getDatabaseProductVersion());

			List<String> tables = new ArrayList<>();
			try (ResultSet result = metadata.getTables(
					catalog, null, "%", new String[]{"TABLE"})) {
				while (result.next()) {
					tables.add(result.getString("TABLE_NAME"));
				}
			}
			tables.sort(String::compareToIgnoreCase);
			System.out.println("AUDIT_TABLES|" + String.join(",", tables));

			List<String> mappedTables = List.of(
					"aplicaciones_pago_membresia", "contactos_promocionales",
					"entradas_diarias", "membresias", "pagos", "planes",
					"roles", "socios", "usuarios");
			for (String table : mappedTables) {
				if (tables.stream().noneMatch(table::equalsIgnoreCase)) {
					System.out.println("AUDIT_MISSING_TABLE|" + table);
					continue;
				}
				List<String> columnsFound = new ArrayList<>();
				try (ResultSet columns = metadata.getColumns(
						catalog, null, table, "%")) {
					while (columns.next()) {
						String name = columns.getString("COLUMN_NAME");
						if (name.toLowerCase().matches(".*(password|contrasena|hash|secret|token).*")) {
							columnsFound.add("[credential_column_omitted]");
						} else {
							columnsFound.add(name + ":" + columns.getString("TYPE_NAME")
									+ ":size=" + columns.getInt("COLUMN_SIZE")
									+ ":scale=" + columns.getInt("DECIMAL_DIGITS")
									+ ":nullable=" + columns.getInt("NULLABLE"));
						}
					}
				}
				System.out.println("AUDIT_COLUMNS|" + table + "|"
						+ String.join(",", columnsFound));
				List<String> foreignKeys = new ArrayList<>();
				try (ResultSet keys = metadata.getImportedKeys(
						catalog, null, table)) {
					while (keys.next()) {
						foreignKeys.add(keys.getString("FKCOLUMN_NAME") + "->"
								+ keys.getString("PKTABLE_NAME") + "."
								+ keys.getString("PKCOLUMN_NAME"));
					}
				}
				System.out.println("AUDIT_FKS|" + table + "|"
						+ String.join(",", foreignKeys));
			}

			List<String> socioUniqueIndexes = new ArrayList<>();
			try (ResultSet indexes = metadata.getIndexInfo(
					catalog, null, "socios", true, false)) {
				while (indexes.next()) {
					String indexName = indexes.getString("INDEX_NAME");
					String columnName = indexes.getString("COLUMN_NAME");
					if (indexName != null && columnName != null) {
						socioUniqueIndexes.add(indexName + ":" + columnName);
					}
				}
			}
			System.out.println("AUDIT_SOCIO_UNIQUE_INDEXES|"
					+ String.join(",", socioUniqueIndexes));
			boolean userEmailIsUnique = false;
			try (ResultSet indexes = metadata.getIndexInfo(
					catalog, null, "usuarios", true, false)) {
				while (indexes.next()) {
					if ("correo".equalsIgnoreCase(
							indexes.getString("COLUMN_NAME"))) {
						userEmailIsUnique = true;
					}
				}
			}
			System.out.println("AUDIT_USER_EMAIL_UNIQUE|" + userEmailIsUnique);
			try (var statement = connection.createStatement();
				 ResultSet duplicates = statement.executeQuery(
						 "SELECT COUNT(*) AS duplicate_groups FROM ("
								 + "SELECT codigo_socio FROM socios "
								 + "GROUP BY codigo_socio HAVING COUNT(*) > 1"
								 + ") duplicate_codes")) {
				duplicates.next();
				System.out.println("AUDIT_DUPLICATE_SOCIO_CODE_GROUPS|"
						+ duplicates.getLong("duplicate_groups"));
			}

			try (var statement = connection.createStatement();
				 ResultSet roles = statement.executeQuery(
						 "SELECT r.nombre, COUNT(u.id_usuario) AS usuarios "
								 + "FROM roles r LEFT JOIN usuarios u ON u.id_rol = r.id_rol "
								 + "GROUP BY r.id_rol, r.nombre ORDER BY r.nombre")) {
				List<String> roleCounts = new ArrayList<>();
				while (roles.next()) {
					roleCounts.add(roles.getString("nombre") + ":"
							+ roles.getLong("usuarios"));
				}
				System.out.println("AUDIT_ROLE_COUNTS|"
						+ String.join(",", roleCounts));
			}
		}
	}

}
