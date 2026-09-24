import { Link } from 'react-router'
import { BrandMetaballs } from '@/shared/brand/BrandMetaballs'
import { Button } from '@/shared/ui'

export function NotFoundPage() {
  return (
    <main className="page page--center page--inset-viewport bg-bg p-lg">
      <section className="grid justify-items-center gap-lg text-center" data-slot="not-found">
        <BrandMetaballs className="size-(--layout-brand-mark-inline-size) rounded-full" />
        <div className="grid justify-items-center gap-sm" data-slot="not-found-body">
          <h1 className="type-hero">页面不存在</h1>
          <p className="type-lead">这个地址没有对应的页面，链接可能已经过期或者输入有误。</p>
        </div>
        <Button render={<Link to="/" />}>返回工作台</Button>
      </section>
    </main>
  )
}
