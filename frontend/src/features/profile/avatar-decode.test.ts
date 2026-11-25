import { expect, test } from 'vite-plus/test'
import { observeAvatarDecode } from './avatar-decode'

function deferred() {
  let resolve!: () => void
  let reject!: (reason: Error) => void
  const promise = new Promise<void>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}

test('avatar switches to ready only after decode succeeds', async () => {
  const decoding = deferred()
  const statuses: string[] = []

  observeAvatarDecode(
    '/avatar.png',
    () => decoding.promise,
    (status) => statuses.push(status),
  )
  expect(statuses).toEqual([])

  decoding.resolve()
  await decoding.promise
  expect(statuses).toEqual(['ready'])
})

test('avatar decode failure requests the placeholder', async () => {
  const decoding = deferred()
  const statuses: string[] = []

  observeAvatarDecode(
    '/broken.png',
    () => decoding.promise,
    (status) => statuses.push(status),
  )
  decoding.reject(new Error('invalid image'))
  await decoding.promise.catch(() => undefined)
  expect(statuses).toEqual(['error'])
})

test('stale avatar decode results are discarded after replacement or unmount', async () => {
  const first = deferred()
  const second = deferred()
  const statuses: string[] = []

  const cancelFirst = observeAvatarDecode(
    '/first.png',
    () => first.promise,
    (status) => statuses.push(`first:${status}`),
  )
  const cancelSecond = observeAvatarDecode(
    '/second.png',
    () => second.promise,
    (status) => statuses.push(`second:${status}`),
  )
  cancelFirst()
  first.resolve()
  second.reject(new Error('replacement failed'))
  await Promise.all([first.promise, second.promise.catch(() => undefined)])
  expect(statuses).toEqual(['second:error'])

  cancelSecond()
  statuses.length = 0
  const third = deferred()
  const cancelThird = observeAvatarDecode(
    '/third.png',
    () => third.promise,
    (status) => statuses.push(status),
  )
  cancelThird()
  third.resolve()
  await third.promise
  expect(statuses).toEqual([])
})
