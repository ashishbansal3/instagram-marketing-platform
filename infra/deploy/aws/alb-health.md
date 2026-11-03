# ALB / target group health checks

Wire each ECS service to a target group:

| Service | Port | Path | Healthy threshold | Interval |
| --- | --- | --- | --- | --- |
| api | 8080 | `/api/v1/health` | 2 | 15s |
| worker-media | 8081 | `/api/v1/health` | 2 | 15s |
| worker-publish | 8082 | `/api/v1/health` | 2 | 15s |
| remotion | 3100 | `/health` | 2 | 15s |

Grace / deregistration delay: ≥ 30–60s so in-flight webhook or media jobs can finish (matches SIGTERM drain expectations).

API and workers return JSON with `"ok": true` on success (HTTP 200). Remotion returns HTTP 200 on `/health`.
