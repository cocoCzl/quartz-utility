#!/usr/bin/env python3
"""Checks reproducible test entry points without executing or downloading build tools."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
NS = {'m': 'http://maven.apache.org/POM/4.0.0'}

class BuildContractTest(unittest.TestCase):
    def test_parent_pins_test_plugin(self):
        root = ET.parse(ROOT / 'pom.xml')
        plugins = root.findall('./m:build/m:pluginManagement/m:plugins/m:plugin', NS)
        self.assertTrue(any(p.findtext('m:artifactId', namespaces=NS) == 'maven-surefire-plugin'
                            and p.findtext('m:version', namespaces=NS) == '3.5.4' for p in plugins))

    def test_wrapper_is_pinned_and_has_entry_points(self):
        self.assertTrue((ROOT / 'mvnw').exists())
        self.assertTrue((ROOT / 'mvnw.cmd').exists())
        self.assertIn('apache-maven-3.9.9', (ROOT / '.mvn/wrapper/maven-wrapper.properties').read_text())

    def test_cluster_ci_is_explicitly_enabled(self):
        ci = (ROOT / '.github/workflows/ci.yml').read_text()
        self.assertIn('-Dco.quartz.test.mysql.url=', ci)
        self.assertIn('mysql:8.4', ci)

    def test_starter_uses_environment_independent_mock_maker(self):
        setting = ROOT / 'co-quartz-starter/src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker'
        self.assertEqual(setting.read_text().strip(), 'mock-maker-subclass')

    def test_ci_supplies_server_root_to_database_creating_cluster_tests(self):
        ci = (ROOT / '.github/workflows/ci.yml').read_text()
        self.assertIn('-Dco.quartz.test.mysql.url=jdbc:mysql://127.0.0.1:3306/', ci)
        self.assertNotIn(':3306/mysql?', ci)

    def test_release_uses_the_same_wrapper_after_java_setup(self):
        release = (ROOT / '.github/workflows/release.yml').read_text()
        self.assertIn('./mvnw -B deploy', release)
        self.assertLess(release.index('actions/setup-java'), release.index('Verify release version'))

if __name__ == '__main__':
    unittest.main()
