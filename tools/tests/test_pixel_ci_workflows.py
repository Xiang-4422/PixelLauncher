#!/usr/bin/env python3
"""锁定 Pixel SDK 必需 CI 与夜间长任务的关键配置契约。"""

from __future__ import annotations

import re
import unittest
from pathlib import Path


# 仓库根目录用于定位工作流和统一发布门禁脚本。
ROOT = Path(__file__).resolve().parents[2]
# PR 与主分支必需工作流的原始文本。
REQUIRED_WORKFLOW = (ROOT / ".github" / "workflows" / "pixel-engine.yml").read_text(
    encoding="utf-8"
)
# 夜间设备矩阵工作流的原始文本。
NIGHTLY_WORKFLOW = (
    ROOT / ".github" / "workflows" / "pixel-engine-nightly.yml"
).read_text(encoding="utf-8")
# 正式版本文档站工作流的原始文本。
DOCUMENTATION_WORKFLOW = (
    ROOT / ".github" / "workflows" / "pixel-engine-docs.yml"
).read_text(encoding="utf-8")
# 本地统一发布门禁的原始文本。
RELEASE_CHECK = (ROOT / "tools" / "pixel-release-check.sh").read_text(
    encoding="utf-8"
)
# 消费者矩阵负责复用同一批 Maven 产物并执行发布物结构校验。
COMPATIBILITY_MATRIX = (
    ROOT / "tools" / "pixel-consumer-compatibility-matrix.sh"
).read_text(encoding="utf-8")


class PixelCiWorkflowContractTest(unittest.TestCase):
    """防止必需 job、失败证据或长任务调度在维护中静默丢失。"""

    def test_required_workflow_keeps_all_blocking_jobs(self) -> None:
        """PR 工作流必须保留七类非性能门禁和严格 required 聚合器。"""

        # 必需 job 标识符与 required.needs 必须一一对应。
        required_jobs = (
            "fast",
            "api",
            "lint",
            "instrumentation",
            "consumer",
            "publication",
            "supply_chain",
        )
        for job_name in required_jobs:
            with self.subTest(job_name=job_name):
                self.assertRegex(REQUIRED_WORKFLOW, rf"(?m)^  {job_name}:$")
                self.assertRegex(REQUIRED_WORKFLOW, rf"(?m)^      - {job_name}$")
        self.assertRegex(REQUIRED_WORKFLOW, r"(?m)^  required:$")
        self.assertIn("if: always()", REQUIRED_WORKFLOW)
        self.assertIn("bash tools/pixel-ci-required-check.sh", REQUIRED_WORKFLOW)
        self.assertNotRegex(REQUIRED_WORKFLOW, r"(?m)^  performance:$")
        self.assertNotIn("needs.performance.result", REQUIRED_WORKFLOW)

    def test_required_workflow_uploads_each_failure_evidence_family(self) -> None:
        """测试、API、golden、设备结果与 AAR 报告必须始终上传。"""

        # 失败后仍需要保留的证据路径关键字。
        evidence_markers = (
            "build/test-results",
            "build/reports/api",
            "build/reports/golden",
            "connected_android_test_additional_output",
            "publication.json",
            "build/compatibility-repository",
            "build/reports/supply-chain",
        )
        for marker in evidence_markers:
            with self.subTest(marker=marker):
                self.assertIn(marker, REQUIRED_WORKFLOW)
        # 每个实体门禁恰好有一个失败也执行的 artifact 上传步骤。
        always_upload_count = len(
            re.findall(
                r"if: always\(\)\s+uses: actions/upload-artifact@v7",
                REQUIRED_WORKFLOW,
            )
        )
        self.assertEqual(7, always_upload_count)
        self.assertNotIn("continue-on-error", REQUIRED_WORKFLOW)

    def test_workflows_disable_build_cache_for_fresh_outputs(self) -> None:
        """关键 Gradle 调用必须禁用 build cache，避免旧产物造成假绿。"""

        self.assertIn("--no-build-cache", REQUIRED_WORKFLOW)
        self.assertIn("--no-build-cache", NIGHTLY_WORKFLOW)

    def test_required_jobs_install_declared_android_platforms(self) -> None:
        """七个实体 PR job 都不能依赖 runner 偶然预装 compileSdk。"""

        # 每个实体 job 都应显式调用同一 Android SDK setup action。
        setup_count = REQUIRED_WORKFLOW.count("uses: android-actions/setup-android@v4")
        self.assertEqual(7, setup_count)
        self.assertEqual(7, REQUIRED_WORKFLOW.count("platforms;android-36.1"))

    def test_required_workflow_verifies_dependencies_with_empty_gradle_home(self) -> None:
        """required 门禁必须从独立 Gradle Home 构建 AndroidTest，不能依赖共享暖缓存。"""

        self.assertIn("Verify dependency metadata from an empty Gradle home", REQUIRED_WORKFLOW)
        self.assertIn("GRADLE_USER_HOME: ${{ runner.temp }}/pixel-cold-gradle-home", REQUIRED_WORKFLOW)
        self.assertIn(
            "./gradlew help :pixel-engine:dependencies :pixel-engine:assembleDebugAndroidTest",
            REQUIRED_WORKFLOW,
        )

    def test_nightly_workflow_keeps_device_matrix(self) -> None:
        """夜间工作流必须保留 API 24、29、36 的 instrumentation 矩阵。"""

        self.assertIn("api-level: [24, 29, 36]", NIGHTLY_WORKFLOW)
        self.assertNotIn("macrobenchmark", NIGHTLY_WORKFLOW.lower())
        self.assertNotIn("device-soak", NIGHTLY_WORKFLOW)
        self.assertIn("bash tools/pixel-ci-required-check.sh", NIGHTLY_WORKFLOW)
        self.assertNotIn("continue-on-error", NIGHTLY_WORKFLOW)

    def test_emulator_readiness_retry_stays_on_one_action_script_line(self) -> None:
        """emulator-runner 会逐行执行 script，SDK 重试循环必须保持为单条 shell 命令。"""

        # 单行前缀同时冻结 required 与 nightly 的 runner 兼容写法。
        retry_prefix = 'device_api=""; for attempt in $(seq 1 12); do'
        self.assertIn(retry_prefix, REQUIRED_WORKFLOW)
        self.assertIn(retry_prefix, NIGHTLY_WORKFLOW)
        self.assertNotIn("\n            for attempt in $(seq 1 12); do", REQUIRED_WORKFLOW)
        self.assertNotIn("\n            for attempt in $(seq 1 12); do", NIGHTLY_WORKFLOW)

    def test_documentation_workflow_deploys_only_from_release_tags(self) -> None:
        """Pages 只能从公开 Release 或手动指定的既有 SemVer tag 严格构建。"""

        # 工作流不得因普通 main push 自动覆盖某个正式版本的文档。
        self.assertIn("release:", DOCUMENTATION_WORKFLOW)
        self.assertIn("- published", DOCUMENTATION_WORKFLOW)
        self.assertIn("workflow_dispatch:", DOCUMENTATION_WORKFLOW)
        self.assertNotRegex(DOCUMENTATION_WORKFLOW, r"(?m)^  push:$")
        # tag、CHANGELOG 和严格 MkDocs 三项共同锁定站点版本。
        self.assertIn("^v[0-9]+\\.[0-9]+\\.[0-9]+$", DOCUMENTATION_WORKFLOW)
        self.assertIn("git tag --points-at HEAD", DOCUMENTATION_WORKFLOW)
        self.assertIn("CHANGELOG 缺少版本条目", DOCUMENTATION_WORKFLOW)
        self.assertIn("python3 -m mkdocs build --strict", DOCUMENTATION_WORKFLOW)
        # Pages 官方三段 action 与最小权限必须完整保留。
        self.assertIn("uses: actions/configure-pages@v5", DOCUMENTATION_WORKFLOW)
        self.assertIn("uses: actions/upload-pages-artifact@v4", DOCUMENTATION_WORKFLOW)
        self.assertIn("uses: actions/deploy-pages@v4", DOCUMENTATION_WORKFLOW)
        self.assertIn("pages: write", DOCUMENTATION_WORKFLOW)
        self.assertIn("id-token: write", DOCUMENTATION_WORKFLOW)
        self.assertIn("name: github-pages", DOCUMENTATION_WORKFLOW)
        self.assertNotIn("performance", DOCUMENTATION_WORKFLOW.lower())

    def test_local_release_check_still_aggregates_no_credential_gates(self) -> None:
        """本地入口必须聚合发布、消费者和供应链门禁，并排除性能专项。"""

        # 统一入口必须保留的无凭据子门禁脚本。
        release_gate_scripts = (
            "pixel-consumer-compatibility-matrix.sh",
            "pixel-supply-chain-check.sh",
        )
        for script_name in release_gate_scripts:
            with self.subTest(script_name=script_name):
                self.assertIn(script_name, RELEASE_CHECK)
        self.assertNotIn("pixel-baseline-profile-check.sh", RELEASE_CHECK)
        self.assertIn("check_pixel_publication.py", COMPATIBILITY_MATRIX)


if __name__ == "__main__":
    unittest.main()
