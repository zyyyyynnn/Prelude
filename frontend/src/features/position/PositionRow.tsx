import { Pencil } from 'lucide-react'
import { Button } from '@/shared/ui/button'

/** One row of the position library. Built-in positions carry no edit action, so the
 *  row keeps its name column and simply renders nothing on the trailing side. */
export function PositionRow({
  name,
  editable = false,
  onEdit,
}: {
  name: string
  editable?: boolean
  onEdit: () => void
}) {
  return (
    <div className="row-label-end" role="listitem">
      <span className="truncate-title" data-slot="position-item-name">
        {name}
      </span>
      {editable && (
        <Button
          type="button"
          size="icon"
          variant="ghost"
          aria-label={`编辑 ${name}`}
          onClick={onEdit}
        >
          <Pencil aria-hidden="true" />
        </Button>
      )}
    </div>
  )
}
