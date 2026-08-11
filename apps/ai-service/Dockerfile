# Multi-stage build: the builder resolves the venv with uv; the runtime image ships ONLY the
# resolved venv and the app. This keeps uv, its build cache (/root/.cache/uv) and the build-time
# packages it vendors (e.g. setuptools, msgpack) OUT of the final image — those cache artifacts,
# not the runtime dependencies, were what tripped the Trivy HIGH/CRITICAL gate.
FROM python:3.13-slim AS builder
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    UV_COMPILE_BYTECODE=1 \
    UV_LINK_MODE=copy
WORKDIR /app
RUN pip install --no-cache-dir uv==0.8.22
COPY pyproject.toml uv.lock ./
COPY app ./app
# Self-contained venv (UV_LINK_MODE=copy) so it carries no hardlinks back into the uv cache.
RUN uv sync --frozen --no-dev

FROM python:3.13-slim AS runtime
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PATH="/app/.venv/bin:$PATH"
WORKDIR /app
RUN addgroup --system app && adduser --system --ingroup app app
# The app runs entirely from /app/.venv, so the base image's global pip is unused at runtime.
# Remove it — its vendored msgpack (1.1.2) is the last package that trips the Trivy HIGH gate.
RUN rm -rf /usr/local/lib/python3.13/site-packages/pip \
           /usr/local/lib/python3.13/site-packages/pip-*.dist-info \
           /usr/local/bin/pip /usr/local/bin/pip3 /usr/local/bin/pip3.13
COPY --from=builder /app/.venv /app/.venv
COPY app ./app
USER app
EXPOSE 8000
CMD ["/app/.venv/bin/uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
