"""RFC 9457 problem details. Clients switch on `code`."""

import logging

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException

log = logging.getLogger("ominira.errors")

MEDIA_TYPE = "application/problem+json"
TYPE_BASE = "https://docs.ominira.app/errors/"


class Problem(Exception):
    def __init__(self, status: int, code: str, title: str, say: str | None = None, **extra):
        super().__init__(f"{status} {code}: {title}")
        self.status = status
        self.code = code
        self.title = title
        self.say = say
        self.extra = extra

    def as_body(self) -> dict:
        body = {"type": TYPE_BASE + self.code, "title": self.title, "status": self.status, "code": self.code}
        if self.say is not None:
            body["say"] = self.say
        body.update(self.extra)
        return body

    def response(self) -> JSONResponse:
        headers = {}
        if "retry_after" in self.extra:
            headers["Retry-After"] = str(self.extra["retry_after"])
        return JSONResponse(self.as_body(), status_code=self.status, media_type=MEDIA_TYPE, headers=headers)


_HTTP_CODES = {401: "unauthorized", 403: "forbidden", 404: "not_found", 405: "method_not_allowed"}


async def _on_problem(_: Request, exc: Problem) -> JSONResponse:
    return exc.response()


async def _on_http(_: Request, exc: HTTPException) -> JSONResponse:
    code = _HTTP_CODES.get(exc.status_code, "error")
    return Problem(exc.status_code, code, str(exc.detail)).response()


async def _on_validation(_: Request, exc: RequestValidationError) -> JSONResponse:
    errors = [{"loc": [str(p) for p in e.get("loc", ())], "msg": e.get("msg", "")} for e in exc.errors()]
    return Problem(422, "invalid_request", "The request is not valid", errors=errors).response()


async def _on_crash(_: Request, exc: Exception) -> JSONResponse:
    log.exception("Unhandled error", exc_info=exc)
    return Problem(500, "internal", "Something went wrong on our side").response()


def install(app: FastAPI) -> None:
    app.add_exception_handler(Problem, _on_problem)
    app.add_exception_handler(HTTPException, _on_http)
    app.add_exception_handler(RequestValidationError, _on_validation)
    app.add_exception_handler(Exception, _on_crash)
