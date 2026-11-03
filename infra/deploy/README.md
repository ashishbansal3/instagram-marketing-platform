# Deploy scaffolding (AWS)

Examples only — **no secrets**, no live apply from CI without Ashish’s AWS account + OIDC role.

| Piece | Path |
| --- | --- |
| Overview + order | [`docs/deploy.md`](../../docs/deploy.md) |
| Pre-deploy Flyway | [`scripts/pre-deploy-migrate.sh`](../../scripts/pre-deploy-migrate.sh) |
| ECS task def sketches | [`aws/ecs/`](aws/ecs/) |
| ALB health notes | [`aws/alb-health.md`](aws/alb-health.md) |
| S3 IAM policy sketch | [`aws/iam-s3-media-policy.json`](aws/iam-s3-media-policy.json) |
| GitHub OIDC trust sketch | [`aws/github-oidc-trust-policy.json`](aws/github-oidc-trust-policy.json) |
| Deploy workflow sketch | [`.github/workflows/deploy-aws.yml`](../../.github/workflows/deploy-aws.yml) |

**Primary:** ECS Fargate + ALB · RDS · ElastiCache · S3 · Amplify (web) · GitHub OIDC.
