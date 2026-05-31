import { http } from './http'
import type {
  ApiResult,
  LoginResponse,
  PositionTemplate,
} from './contracts'
import { unwrapResult } from './contracts'

export async function login(username: string, password: string) {
  const response = await http.post<ApiResult<LoginResponse>>('/auth/login', {
    username,
    password,
  })
  return unwrapResult(response.data)
}

export async function register(username: string, password: string, email?: string) {
  const response = await http.post<ApiResult<void>>('/auth/register', {
    username,
    password,
    email,
  })
  return unwrapResult(response.data)
}

export async function fetchPositions() {
  const response = await http.get<ApiResult<PositionTemplate[]>>('/position/list')
  return unwrapResult(response.data)
}
