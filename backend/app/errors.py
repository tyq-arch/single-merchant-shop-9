"""业务异常定义。

服务层抛出 BusinessError，由 main.py 统一转换为 JSON 错误响应，
避免服务层依赖 Web 框架细节。
"""


class BusinessError(Exception):
    def __init__(self, message: str, status_code: int = 400, code: str = "BUSINESS_ERROR"):
        super().__init__(message)
        self.message = message
        self.status_code = status_code
        self.code = code


class NotFound(BusinessError):
    def __init__(self, message: str = "资源不存在"):
        super().__init__(message, status_code=404, code="NOT_FOUND")


class Conflict(BusinessError):
    """状态冲突，例如重复发布、冻结中提交意向等。"""

    def __init__(self, message: str):
        super().__init__(message, status_code=409, code="CONFLICT")


class Unauthorized(BusinessError):
    def __init__(self, message: str = "未登录或凭证无效"):
        super().__init__(message, status_code=401, code="UNAUTHORIZED")


class InvalidCode(BusinessError):
    def __init__(self, message: str = "口令码无效或已失效"):
        super().__init__(message, status_code=410, code="COMMAND_CODE_INVALID")
