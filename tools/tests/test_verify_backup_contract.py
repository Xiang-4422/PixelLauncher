from __future__ import annotations

import unittest

from tools import verify_backup_contract


class BackupContractVerifierTest(unittest.TestCase):
    """Locks exact source-independent validation of compiled backup rule semantics."""

    def test_validate_contract_requires_all_three_backup_scopes(self) -> None:
        """API 24–30, cloud backup, and device transfer must all exclude the legacy file."""

        manifest = f"""
            <manifest xmlns:android="{verify_backup_contract.ANDROID_NAMESPACE}">
              <application
                android:fullBackupContent="@xml/backup_rules"
                android:dataExtractionRules="@xml/data_extraction_rules" />
            </manifest>
        """
        full_backup = """
            <full-backup-content>
              <exclude domain="sharedpref" path="app_repository_cache.xml" />
            </full-backup-content>
        """
        data_extraction = """
            <data-extraction-rules>
              <cloud-backup>
                <exclude domain="sharedpref" path="app_repository_cache.xml" />
              </cloud-backup>
              <device-transfer>
                <exclude domain="sharedpref" path="app_repository_cache.xml" />
              </device-transfer>
            </data-extraction-rules>
        """

        evidence = verify_backup_contract.validate_contract(
            manifest,
            full_backup,
            data_extraction,
            "sample.apk",
        )

        self.assertTrue(evidence.manifest_rules)
        self.assertTrue(evidence.full_backup_shared_preferences)
        self.assertTrue(evidence.cloud_backup_shared_preferences)
        self.assertTrue(evidence.device_transfer_shared_preferences)

    def test_validate_contract_rejects_missing_device_transfer_exclusion(self) -> None:
        """Cloud exclusion alone cannot prove device-to-device migration is safe."""

        manifest = f"""
            <manifest xmlns:android="{verify_backup_contract.ANDROID_NAMESPACE}">
              <application
                android:fullBackupContent="@xml/backup_rules"
                android:dataExtractionRules="@xml/data_extraction_rules" />
            </manifest>
        """
        full_backup = """
            <full-backup-content>
              <exclude domain="sharedpref" path="app_repository_cache.xml" />
            </full-backup-content>
        """
        data_extraction = """
            <data-extraction-rules>
              <cloud-backup>
                <exclude domain="sharedpref" path="app_repository_cache.xml" />
              </cloud-backup>
              <device-transfer />
            </data-extraction-rules>
        """

        with self.assertRaisesRegex(ValueError, "cloud/device"):
            verify_backup_contract.validate_contract(
                manifest,
                full_backup,
                data_extraction,
                "sample.apk",
            )


if __name__ == "__main__":
    unittest.main()
