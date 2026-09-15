import { ElMessage } from 'element-plus'

export const handleError = (error: unknown, message: string) => {
  console.error(message, error)
  ElMessage.error(message)
}
