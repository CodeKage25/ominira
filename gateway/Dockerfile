# The Ominira Gateway. Cloud Run sets PORT; the demo keeps its SQLite file in /tmp.
FROM python:3.12-slim

RUN pip install --no-cache-dir uv==0.11.32
# ffmpeg turns YarnGPT's MP3 into the live call's 8 kHz μ-law: MP3 arrives about three times sooner than WAV.
RUN apt-get update && apt-get install -y --no-install-recommends ffmpeg && rm -rf /var/lib/apt/lists/*

WORKDIR /srv
ENV UV_COMPILE_BYTECODE=1 UV_LINK_MODE=copy UV_PYTHON_DOWNLOADS=never

# Dependencies first, so code changes do not reinstall them.
COPY pyproject.toml uv.lock ./
RUN uv sync --frozen --no-dev --no-install-project

COPY ominira ./ominira
COPY scripts ./scripts

ENV PATH="/srv/.venv/bin:$PATH" \
    PYTHONUNBUFFERED=1 \
    PORT=8080 \
    DATABASE_URL=sqlite:////tmp/ominira.db

# Run as an unprivileged user. It can write only to /tmp, which is where the SQLite file lives.
USER nobody

EXPOSE 8080
CMD ["sh", "-c", "exec uvicorn ominira.main:app --host 0.0.0.0 --port ${PORT} --proxy-headers --forwarded-allow-ips='*'"]
