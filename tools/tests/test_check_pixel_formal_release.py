#!/usr/bin/env python3
"""验证正式发布预检不会把候选 staging 误判为不可变发布。"""

from __future__ import annotations

import unittest

from tools.check_pixel_formal_release import ARTIFACTS, evaluate_formal_release


class PixelFormalReleasePreflightTest(unittest.TestCase):
    """覆盖全部正式条件就绪和候选外部条件缺失两种状态。"""

    def test_all_confirmed_conditions_are_ready(self) -> None:
        """namespace、仓库、签名、Git 与 GitHub 全部满足时才返回 ready。"""

        # 正式发布元数据包含可审计 namespace、远端仓库和长期指纹。
        metadata = {
            "groupId": "com.purride",
            "namespaceStatus": "CONFIRMED",
            "namespaceProofUrl": "dns://purride.com/TXT",
            "releaseRepositoryStatus": "CONFIRMED",
            "releaseRepositoryUrl": "https://central.example/releases",
            "signingIdentityStatus": "CONFIRMED",
            "signingFingerprint": "A" * 40,
        }
        # 九个模块必须共享完全相同的正式 GAV 前缀。
        coordinates = {
            artifact: {"group": "com.purride", "version": "1.0.0"}
            for artifact in ARTIFACTS
        }
        # GitHub 最终报告证明 tag、Release、Pages 与保护规则已经就绪。
        github_report = {
            "status": "ready",
            "version": "1.0.0",
            "checkedAtUtc": "2026-07-16T00:00:00Z",
        }
        # Support 状态必须明确为缓存清除完成。
        support_report = {"status": "cleared"}

        # 全部事实成立时预检不得保留任何缺失项。
        report = evaluate_formal_release(
            version="1.0.0",
            metadata=metadata,
            module_coordinates=coordinates,
            changelog_dated=True,
            clean_worktree=True,
            version_tag_at_head=True,
            head_on_origin_main=True,
            github_report=github_report,
            github_report_fresh=True,
            support_report=support_report,
        )
        self.assertEqual("ready", report["status"])
        self.assertEqual([], report["missing"])
        self.assertTrue(all(report["checks"].values()))

    def test_candidate_state_reports_every_formal_release_gap(self) -> None:
        """一次性 staging、脏树和缺失外部状态必须逐项保持 incomplete。"""

        # 候选元数据故意不确认 namespace、仓库和签名身份。
        metadata = {
            "groupId": "com.purride",
            "namespaceStatus": "UNCONFIRMED",
            "namespaceProofUrl": "",
            "releaseRepositoryStatus": "UNCONFIRMED",
            "releaseRepositoryUrl": "",
            "signingIdentityStatus": "UNCONFIRMED",
            "signingFingerprint": "",
        }
        # 候选模块坐标本身保持一致，以隔离外部发布缺口。
        coordinates = {
            artifact: {"group": "com.purride", "version": "1.0.0"}
            for artifact in ARTIFACTS
        }

        # 当前候选必须精确列出除坐标一致性之外的十项缺口。
        report = evaluate_formal_release(
            version="1.0.0",
            metadata=metadata,
            module_coordinates=coordinates,
            changelog_dated=False,
            clean_worktree=False,
            version_tag_at_head=False,
            head_on_origin_main=False,
            github_report={"status": "incomplete", "version": "1.0.0"},
            github_report_fresh=False,
            support_report={"status": "pending-support"},
        )
        self.assertEqual("incomplete", report["status"])
        self.assertTrue(report["checks"]["coordinatesConsistent"])
        self.assertEqual(10, len(report["missing"]))
        self.assertNotIn("coordinatesConsistent", report["missing"])


if __name__ == "__main__":
    unittest.main()
