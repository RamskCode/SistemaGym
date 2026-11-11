import type { HTMLAttributes, ReactNode } from 'react'

type CardProps = HTMLAttributes<HTMLElement> & {
  title?: string
  action?: ReactNode
  children: ReactNode
}

export function Card({ title, action, children, className = '', ...props }: CardProps) {
  return (
    <section className={`card ${className}`.trim()} {...props}>
      {(title || action) && (
        <header className="card__header">
          {title && <h2 className="card__title">{title}</h2>}
          {action}
        </header>
      )}
      {children}
    </section>
  )
}
