"""请求体数据模型（Pydantic）。"""
import re
from typing import Literal, Optional

from pydantic import BaseModel, Field, field_validator, model_validator

_PHONE_RE = re.compile(r"^[0-9+\-\s]{5,20}$")


class SellerLoginRequest(BaseModel):
    username: str = Field(min_length=1, max_length=64)
    password: str = Field(min_length=1, max_length=128)


class ChangePasswordRequest(BaseModel):
    old_password: str = Field(min_length=1, max_length=128)
    new_password: str = Field(min_length=6, max_length=128)


class ProductCreateRequest(BaseModel):
    name: str = Field(min_length=1, max_length=120)
    price: float = Field(gt=0, le=9_999_999)
    description: Optional[str] = Field(default=None, max_length=2000)
    image_url: Optional[str] = Field(default=None, max_length=500)

    @field_validator("name")
    @classmethod
    def _strip_name(cls, value: str) -> str:
        value = value.strip()
        if not value:
            raise ValueError("商品名称不能为空")
        return value


class BuyerIntentCreateRequest(BaseModel):
    product_id: str = Field(min_length=1)
    buyer_name: str = Field(min_length=1, max_length=64)
    buyer_phone: str = Field(min_length=5, max_length=20)

    @field_validator("buyer_name")
    @classmethod
    def _check_name(cls, value: str) -> str:
        value = value.strip()
        if not value:
            raise ValueError("姓名不能为空")
        return value

    @field_validator("buyer_phone")
    @classmethod
    def _check_phone(cls, value: str) -> str:
        value = value.strip()
        if not _PHONE_RE.match(value):
            raise ValueError("联系电话格式不正确")
        return value


class BuyerIntentUpdateRequest(BaseModel):
    code: str = Field(min_length=1)
    buyer_name: Optional[str] = Field(default=None, max_length=64)
    buyer_phone: Optional[str] = Field(default=None, max_length=20)

    @field_validator("buyer_name")
    @classmethod
    def _check_name(cls, value: Optional[str]) -> Optional[str]:
        if value is None:
            return None
        value = value.strip()
        if not value:
            raise ValueError("姓名不能为空")
        return value

    @field_validator("buyer_phone")
    @classmethod
    def _check_phone(cls, value: Optional[str]) -> Optional[str]:
        if value is None:
            return None
        value = value.strip()
        if not _PHONE_RE.match(value):
            raise ValueError("联系电话格式不正确")
        return value

    @model_validator(mode="after")
    def _at_least_one(self):
        if self.buyer_name is None and self.buyer_phone is None:
            raise ValueError("至少需要提交姓名或联系电话中的一项")
        return self


class BuyerCodeRequest(BaseModel):
    code: str = Field(min_length=1)


class TransactionMarkRequest(BaseModel):
    result: Literal["SUCCESS", "FAILED"]
    note: Optional[str] = Field(default=None, max_length=500)
