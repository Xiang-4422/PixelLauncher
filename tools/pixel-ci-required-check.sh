#!/usr/bin/env bash
set -euo pipefail

# required 聚合器只接受所有上游 job 明确成功，skipped/cancelled/failure 均阻止候选发布。
if [[ "$#" -eq 0 ]]; then
  echo "required gate received no job results" >&2
  exit 2
fi

# 当前检查的上游 job 序号。
job_index=0
for job_result in "$@"; do
  job_index=$((job_index + 1))
  if [[ "$job_result" != "success" ]]; then
    echo "required gate rejected job $job_index with result: $job_result" >&2
    exit 1
  fi
done

echo "Pixel required CI gate passed for $# jobs."
