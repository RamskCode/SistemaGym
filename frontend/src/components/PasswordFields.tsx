import { Check, Circle, Eye, EyeOff } from 'lucide-react'
import { useState } from 'react'
import { passwordPolicyError, passwordRequirements, type PasswordRequirement } from '../utils/passwordPolicy'
import './password-fields.css'

type PasswordInputProps = {
  id: string
  label: string
  value: string
  onChange: (value: string) => void
  autoComplete?: 'new-password' | 'current-password'
  describedBy?: string
  className?: string
}

export function PasswordInput({
  id, label, value, onChange, autoComplete = 'new-password', describedBy, className = '',
}: PasswordInputProps) {
  const [visible, setVisible] = useState(false)
  const toggleName = label.toLocaleLowerCase('es-PE').replace(/^confirmar\s/u, 'confirmación de ')
  return <div className={`password-field ${className}`.trim()}>
    <label htmlFor={id}>{label}</label>
    <div className="password-field__control">
      <input className="password-field__input" autoComplete={autoComplete} id={id} onChange={(event) => onChange(event.target.value)} required
        type={visible ? 'text' : 'password'} value={value} aria-describedby={describedBy} />
      <button aria-label={`${visible ? 'Ocultar' : 'Mostrar'} ${toggleName}`}
        aria-pressed={visible} onClick={() => setVisible((current) => !current)} type="button">
        {visible ? <EyeOff aria-hidden="true" size={17} /> : <Eye aria-hidden="true" size={17} />}
      </button>
    </div>
  </div>
}

export function PasswordRequirements({ password, id = 'password-requirements' }: {
  password: string
  id?: string
}) {
  const requirements = passwordRequirements(password)
  return <ul aria-label="Requisitos de contraseña" className="password-requirements" id={id}>
    {requirements.map((requirement: PasswordRequirement) => <li aria-label={`${requirement.label}: ${requirement.valid ? 'cumplido' : 'pendiente'}`}
      className={requirement.valid ? 'is-valid' : ''} key={requirement.id}>
      {requirement.valid ? <Check aria-hidden="true" size={16} /> : <Circle aria-hidden="true" size={16} />}
      <span>{requirement.label}</span>
      <span className="password-field__sr-only">{requirement.valid ? 'Cumplido' : 'Pendiente'}</span>
    </li>)}
  </ul>
}

export function PasswordMatchIndicator({ password, confirmation }: {
  password: string
  confirmation: string
}) {
  if (!confirmation) return null
  const matches = Boolean(password) && password === confirmation
  return <p aria-live="polite" className={`password-match${matches ? ' is-valid' : ' is-invalid'}`} role="status">
    {matches ? <Check aria-hidden="true" size={15} /> : <Circle aria-hidden="true" size={15} />}
    {matches ? 'Las contraseñas coinciden.' : 'Las contraseñas aún no coinciden.'}
  </p>
}

export function PasswordPolicyFeedback({ password }: { password: string }) {
  if (!password || passwordRequirements(password).some(({ valid }) => !valid)) return null
  const error = passwordPolicyError(password)
  return error ? <p className="password-policy-feedback" role="status">{error}</p> : null
}
