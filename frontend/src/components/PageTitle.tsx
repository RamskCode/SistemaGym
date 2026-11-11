import type { ReactNode } from 'react'

type PageTitleProps = {
  eyebrow?: string
  title: string
  description?: string
  actions?: ReactNode
}

export function PageTitle({ eyebrow, title, description, actions }: PageTitleProps) {
  return (
    <div className="page-title">
      <div>
        {eyebrow && <p className="page-title__eyebrow">{eyebrow}</p>}
        <h1>{title}</h1>
        {description && <p className="page-title__description">{description}</p>}
      </div>
      {actions && <div className="page-title__actions">{actions}</div>}
    </div>
  )
}
