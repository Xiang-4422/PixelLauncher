#!/usr/bin/env python3
"""验证 GitHub 正式发布状态检查不会把缺失外部配置误判为就绪。"""

from __future__ import annotations

import unittest

from tools.check_pixel_github_release import evaluate_release_readiness


class PixelGithubReleaseReadinessTest(unittest.TestCase):
    """覆盖完整就绪与全部关键外部条件缺失两种边界。"""

    def test_complete_release_state_is_ready(self) -> None:
        """保护规则、Pages、tag 和公开 Release 完整时才允许就绪。"""

        # 完整仓库元数据代表公开、可用且默认分支正确。
        repository_metadata = {
            "visibility": "public",
            "default_branch": "main",
            "archived": False,
            "disabled": False,
        }
        # 保护规则同时覆盖 strict 更新要求和稳定 required context。
        protection = {
            "required_status_checks": {
                "strict": True,
                "contexts": [],
                "checks": [{"context": "Required pixel-engine gate", "app_id": 15368}],
            }
        }
        # Pages 必须由受审 GitHub Actions 工作流部署。
        pages = {
            "build_type": "workflow",
            "html_url": "https://xiang-4422.github.io/PixelLauncher/",
        }
        # 两个工作流必须已经存在于远端且处于 active 状态。
        active_workflow = {"state": "active"}
        # 正式 tag 使用不可变的 v1.0.0 ref。
        tag = {"ref": "refs/tags/v1.0.0"}
        # 正式 Release 不允许是 draft 或 prerelease。
        release = {
            "tag_name": "v1.0.0",
            "draft": False,
            "prerelease": False,
            "published_at": "2026-07-16T00:00:00Z",
            "html_url": "https://github.com/Xiang-4422/PixelLauncher/releases/tag/v1.0.0",
        }

        # 当前归一化结果应没有任何未完成项。
        report = evaluate_release_readiness(
            repository="Xiang-4422/PixelLauncher",
            branch="main",
            version="1.0.0",
            required_context="Required pixel-engine gate",
            repository_metadata=repository_metadata,
            protection=protection,
            pages=pages,
            required_workflow=active_workflow,
            documentation_workflow=active_workflow,
            tag=tag,
            release=release,
        )
        self.assertEqual("ready", report["status"])
        self.assertEqual([], report["missing"])
        self.assertTrue(all(report["checks"].values()))

    def test_missing_external_state_is_reported_instead_of_ignored(self) -> None:
        """未配置保护、Pages、文档工作流、tag 或 Release 时必须明确失败。"""

        # 仓库本身可用，但所有发布态外部对象都故意缺失。
        repository_metadata = {
            "visibility": "public",
            "default_branch": "main",
            "archived": False,
            "disabled": False,
        }
        # required workflow 已存在，确保测试只拒绝其余缺失条件。
        required_workflow = {"state": "active"}

        # 当前报告必须准确列出五个尚未配置的外部条件。
        report = evaluate_release_readiness(
            repository="Xiang-4422/PixelLauncher",
            branch="main",
            version="1.0.0",
            required_context="Required pixel-engine gate",
            repository_metadata=repository_metadata,
            protection=None,
            pages=None,
            required_workflow=required_workflow,
            documentation_workflow=None,
            tag=None,
            release=None,
        )
        self.assertEqual("incomplete", report["status"])
        self.assertEqual(
            [
                "documentationWorkflowActive",
                "pagesConfigured",
                "releasePublished",
                "requiredGateBound",
                "versionTagPresent",
            ],
            report["missing"],
        )


if __name__ == "__main__":
    unittest.main()
