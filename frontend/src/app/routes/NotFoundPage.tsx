import { Link } from 'react-router'

export function NotFoundPage() {
  return (
    <main className="not-found-page">
      <div className="not-found-page__content">
        <h1>页面不存在</h1>
        <Link className="not-found-page__link" to="/">
          返回 Prelude
        </Link>
      </div>
    </main>
  )
}
