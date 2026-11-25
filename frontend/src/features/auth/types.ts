export type LoginResponse = {
  accountId: number
}

export type CurrentUser = {
  accountId: number
  username: string
}

export type OAuthProvider = 'google' | 'github'
