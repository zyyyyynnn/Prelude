import type { RefObject } from 'react'

/** A file picker with no visible box of its own: a button elsewhere opens it, so this declares
 *  only the accessible name and the accepted types. The selection is cleared after every pick —
 *  without that, choosing the same file twice in a row fires no change event at all. */
export function HiddenFileInput({
  id,
  label,
  accept,
  multiple,
  inputRef,
  onFiles,
}: {
  id: string
  label: string
  accept: string
  multiple?: boolean
  inputRef?: RefObject<HTMLInputElement | null>
  onFiles: (files: FileList) => void
}) {
  return (
    <>
      <label className="sr-only" htmlFor={id}>
        {label}
      </label>
      <input
        id={id}
        ref={inputRef}
        className="sr-only"
        type="file"
        multiple={multiple}
        accept={accept}
        onChange={(event) => {
          const files = event.target.files
          if (files?.length) onFiles(files)
          event.currentTarget.value = ''
        }}
      />
    </>
  )
}
