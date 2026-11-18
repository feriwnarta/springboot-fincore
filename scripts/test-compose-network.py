#!/usr/bin/env python3

import json
from pathlib import Path
import subprocess
import unittest


ROOT = Path(__file__).resolve().parent.parent
CONFIGURATIONS = (
    (),
    ("-f", "docker-compose.yml", "-f", "docker-compose.scale.yml", "--profile", "scale"),
)


def compose_services(options):
    rendered = subprocess.check_output(
        ["docker", "compose", *options, "config", "--format", "json"], cwd=ROOT, text=True
    )
    return json.loads(rendered)["services"]


class ComposeNetworkTest(unittest.TestCase):
    def test_projection_stores_are_internal_and_kafka_is_loopback_only(self):
        for options in CONFIGURATIONS:
            with self.subTest(options=options):
                services = compose_services(options)
                for name in ("elasticsearch", "mongodb"):
                    self.assertFalse(services[name].get("ports"), f"{name} publishes a host port")
                    self.assertIn("healthcheck", services[name])
                ports = services["kafka"].get("ports", [])
                self.assertEqual(len(ports), 1)
                self.assertEqual(ports[0].get("host_ip"), "127.0.0.1")
                self.assertEqual(ports[0]["target"], 9092)
                self.assertIn("localhost:19092", " ".join(services["kafka"]["healthcheck"]["test"]))
                self.assertEqual(services["search-service"]["environment"]["KAFKA_BOOTSTRAP_SERVERS"], "kafka:19092")


    def test_kong_publishes_only_the_proxy_and_keeps_admin_on_container_loopback(self):
        for options in CONFIGURATIONS:
            with self.subTest(options=options):
                kong = compose_services(options)["kong"]
                self.assertEqual(
                    [(port["target"], str(port["published"])) for port in kong["ports"]],
                    [(8000, "8000")],
                )
                environment = kong["environment"]
                self.assertEqual(environment["KONG_PROXY_LISTEN"], "0.0.0.0:8000")
                self.assertEqual(environment["KONG_ADMIN_LISTEN"], "127.0.0.1:8001")
                self.assertEqual(environment["KONG_DECLARATIVE_CONFIG"], "/kong/declarative/kong.yml")
                self.assertIn("kong health", kong["healthcheck"]["test"])


if __name__ == "__main__":
    unittest.main()
