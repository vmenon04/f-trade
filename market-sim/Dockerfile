# Simulated market data, in place of Fauxnance. Standard library only, so there is nothing to install.
# Built from ./market-sim; the instrument list comes from the repo root through the "project" build context.
FROM python:3.13-slim

WORKDIR /app
COPY server.py .
COPY --from=project src/main/resources/sample-data.sql .
USER nobody

CMD ["python", "server.py"]
