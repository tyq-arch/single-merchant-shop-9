"""在线购物系统（单卖家版）后端服务入口。

启动：
    cd backend
    python -m uvicorn app.main:app --reload --port 8000
接口文档（Swagger）：http://127.0.0.1:8000/docs
"""
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from fastapi.staticfiles import StaticFiles

from . import config
from .errors import BusinessError
from .init_db import init_db
from .routers import buyer, files, public, seller


@asynccontextmanager
async def lifespan(app: FastAPI):
    init_db()
    yield


app = FastAPI(
    title="在线购物系统（单卖家版）后端 API",
    description="单卖家 + 匿名买家；口令码认领意向；先到先得排队；线下交易。",
    version="1.0.0",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.exception_handler(BusinessError)
async def business_error_handler(request: Request, exc: BusinessError):
    return JSONResponse(
        status_code=exc.status_code,
        content={"error": {"code": exc.code, "message": exc.message}},
    )


@app.exception_handler(RequestValidationError)
async def validation_error_handler(request: Request, exc: RequestValidationError):
    errors = exc.errors()
    # 只保留可 JSON 序列化的字段，避免把 ValueError 等对象直接写入响应
    details = [
        {"loc": list(item.get("loc", [])), "msg": item.get("msg"), "type": item.get("type")}
        for item in errors
    ]
    message = errors[0].get("msg", "请求参数不合法") if errors else "请求参数不合法"
    return JSONResponse(
        status_code=422,
        content={"error": {"code": "VALIDATION_ERROR", "message": message, "details": details}},
    )


app.include_router(public.router)
app.include_router(buyer.router)
app.include_router(seller.router)
app.include_router(files.router)

config.ensure_dirs()
app.mount("/uploads", StaticFiles(directory=str(config.UPLOAD_DIR)), name="uploads")


@app.get("/", tags=["系统"], summary="服务信息")
def root():
    return {
        "name": "single-merchant-shop backend",
        "version": "1.0.0",
        "docs": "/docs",
        "redoc": "/redoc",
    }
