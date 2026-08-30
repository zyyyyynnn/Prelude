import { useState } from 'react'
import { Bell } from 'lucide-react'
import { Button, Field, IconTooltip, Input, Modal, Select, Textarea } from '@/shared/ui'

export function ComponentLab() {
  const [dialogOpen, setDialogOpen] = useState(false)
  return (
    <section className="workspace-page">
      <header className="workspace-header">
        <div className="workspace-header__main">
          <div className="workspace-header__title-area">
            <h1 className="workspace-header__title">Component Lab</h1>
          </div>
        </div>
      </header>
      <div className="workspace-page__content scrollable">
        <div className="page-grid">
          <section className="panel">
            <div className="panel__head">
              <h2 className="panel__title">Button</h2>
            </div>
            <div className="settings-inline-actions">
              <Button>主要操作</Button>
              <Button variant="secondary">次要操作</Button>
              <Button variant="ghost">轻操作</Button>
              <Button variant="danger">破坏性操作</Button>
              <Button loading>处理中</Button>
              <Button disabled>不可用</Button>
            </div>
          </section>
          <section className="panel">
            <div className="panel__head">
              <h2 className="panel__title">Field</h2>
            </div>
            <div className="form-grid">
              <Field label="输入框" htmlFor="lab-input">
                <Input id="lab-input" placeholder="输入内容" />
              </Field>
              <Field label="选择器" htmlFor="lab-select">
                <Select
                  id="lab-select"
                  value="deepseek"
                  options={[
                    { value: 'deepseek', label: 'DeepSeek' },
                    { value: 'openai-responses', label: 'OpenAI Responses' },
                  ]}
                  onValueChange={() => undefined}
                />
              </Field>
              <Field label="多行输入" htmlFor="lab-textarea">
                <Textarea id="lab-textarea" placeholder="输入多行内容" />
              </Field>
            </div>
          </section>
          <section className="panel">
            <div className="panel__head">
              <h2 className="panel__title">Overlay</h2>
            </div>
            <div className="settings-inline-actions">
              <IconTooltip label="通知">
                <Button size="icon" variant="secondary" aria-label="通知">
                  <Bell size={16} />
                </Button>
              </IconTooltip>
              <Button variant="secondary" onClick={() => setDialogOpen(true)}>
                打开 Dialog
              </Button>
            </div>
          </section>
        </div>
      </div>
      <Modal open={dialogOpen} onOpenChange={setDialogOpen} title="Dialog">
        <div className="panel-content-wrapper">
          <h2 className="panel__title">Dialog</h2>
          <p className="helper-text">Prelude 浮层组件</p>
          <div className="settings-inline-actions">
            <Button onClick={() => setDialogOpen(false)}>确认</Button>
          </div>
        </div>
      </Modal>
    </section>
  )
}
