"""Regression gate direction, completeness and invalid-data checks."""
import importlib.util
from pathlib import Path
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location("compare_performance", Path(__file__).with_name("compare-performance.py"))
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class ComparePerformanceTest(unittest.TestCase):
    def test_latency_and_throughput_have_opposite_directions(self):
        with tempfile.TemporaryDirectory() as directory:
            base, current = Path(directory) / "base", Path(directory) / "current"
            base.mkdir()
            current.mkdir()
            header = "metric,unit,count,mean,p50,p95,max\n"
            (base / "case-summary.csv").write_text(header + "parse,ms,7,10,10,10,10\nfps,Hz,1,60,60,60,60\n", encoding="utf-8")
            (current / "case-summary.csv").write_text(header + "parse,ms,7,20,20,20,20\nfps,Hz,1,30,30,30,30\n", encoding="utf-8")
            self.assertEqual(2, len(MODULE.compare(base, current, .2)))
            self.assertEqual([], MODULE.compare(current, base, .2))

    def test_missing_scenario_fails(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)
            (path / "case-summary.csv").write_text("metric,unit,count,mean,p50,p95,max\n", encoding="utf-8")
            self.assertEqual(1, len(MODULE.compare(path, path / "missing", .2)))

    def test_empty_baseline_fails(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaises(ValueError):
                MODULE.compare(Path(directory), Path(directory), .2)

    def test_invalid_timing_fails(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)
            (path / "case-summary.csv").write_text("metric,unit,count,mean,p50,p95,max\nparse,ms,1,nan,nan,nan,nan\n", encoding="utf-8")
            with self.assertRaises(ValueError):
                MODULE.compare(path, path, .2)

    def test_missing_metric_and_changed_unit_fail(self):
        with tempfile.TemporaryDirectory() as directory:
            base, current = Path(directory) / "base", Path(directory) / "current"
            base.mkdir()
            current.mkdir()
            header = "metric,unit,count,mean,p50,p95,max\n"
            (base / "case-summary.csv").write_text(header + "parse,ms,1,10,10,10,10\nwrite,ms,1,10,10,10,10\n", encoding="utf-8")
            (current / "case-summary.csv").write_text(header + "parse,Hz,1,10,10,10,10\n", encoding="utf-8")
            self.assertEqual(2, len(MODULE.compare(base, current, .2)))

    def test_empty_scenario_fails(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)
            (path / "case-summary.csv").write_text("metric,unit,count,mean,p50,p95,max\n", encoding="utf-8")
            with self.assertRaises(ValueError):
                MODULE.compare(path, path, .2)
