"""文件接口：商品主图上传（最多一张，限制格式与大小）。"""
import os

from fastapi import APIRouter, File, UploadFile

from .. import config
from ..errors import BusinessError
from ..utils import new_id

router = APIRouter(prefix="/api/files", tags=["文件"])


@router.post("/images", summary="上传商品主图")
def upload_image(file: UploadFile = File(...)):
    ext = os.path.splitext(file.filename or "")[1].lower()
    if ext not in config.ALLOWED_IMAGE_EXT:
        raise BusinessError("仅支持 jpg / jpeg / png / gif / webp 格式的图片",
                            status_code=400, code="INVALID_IMAGE_FORMAT")

    contents = file.file.read(config.MAX_IMAGE_BYTES + 1)
    if len(contents) > config.MAX_IMAGE_BYTES:
        raise BusinessError(f"图片大小不能超过 {config.MAX_IMAGE_BYTES // (1024 * 1024)}MB",
                            status_code=400, code="IMAGE_TOO_LARGE")
    if not contents:
        raise BusinessError("上传文件为空", status_code=400, code="EMPTY_FILE")

    config.ensure_dirs()
    filename = f"{new_id()}{ext}"
    with open(config.UPLOAD_DIR / filename, "wb") as fh:
        fh.write(contents)
    return {"url": f"/uploads/{filename}", "original_name": file.filename, "size": len(contents)}
