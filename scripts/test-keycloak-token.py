import json
import os
import subprocess
import threading
import unittest
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
from urllib.parse import parse_qs


SCRIPT = Path(__file__).resolve().with_name("keycloak-token.sh")
ENDPOINT = "/realms/fincore/protocol/openid-connect/token"
TOKEN = "demo.header.signature"


class TokenEndpoint(BaseHTTPRequestHandler):
    def do_POST(self):
        body = self.rfile.read(int(self.headers["Content-Length"])).decode()
        self.server.requests.append((self.path, parse_qs(body)))
        status, response = self.server.response
        self.send_response(status)
        if status == 302:
            self.send_header("Location", "/redirected")
        self.end_headers()
        self.wfile.write(response.encode())

    def log_message(self, *_args):
        pass


class TokenHelperTest(unittest.TestCase):
    def setUp(self):
        self.server = HTTPServer(("127.0.0.1", 0), TokenEndpoint)
        self.server.requests = []
        self.thread = threading.Thread(
            target=self.server.serve_forever, kwargs={"poll_interval": 0.01}, daemon=True,
        )
        self.thread.start()
        self.addCleanup(self.server.server_close)
        self.addCleanup(self.thread.join)
        self.addCleanup(self.server.shutdown)
        self.env = {key: value for key, value in os.environ.items() if not key.startswith("KEYCLOAK_")}
        self.env["KEYCLOAK_URL"] = f"http://127.0.0.1:{self.server.server_port}"

    def request(self, status, body, trace=False):
        self.server.response = status, body
        command = ["bash", "-x", str(SCRIPT)] if trace else [str(SCRIPT)]
        return subprocess.run(command, env=self.env, capture_output=True, text=True, timeout=10)

    def assert_rejected(self, result):
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(result.stdout, "")
        self.assertNotIn(TOKEN, result.stderr)

    def test_success_emits_one_line_and_sends_default_credentials(self):
        result = self.request(200, json.dumps({"access_token": TOKEN, "refresh_token": "ignore"}))
        self.assertEqual((result.returncode, result.stdout, result.stderr), (0, TOKEN + "\n", ""))
        self.assertEqual(self.server.requests, [(ENDPOINT, {
            "grant_type": ["password"], "client_id": ["fincore-cli"],
            "username": ["customer1"], "password": ["customer1"],
        })])

    def test_overrides_are_encoded_and_password_is_not_traced(self):
        password = "a + & % = demo password"
        self.env["KEYCLOAK_URL"] += "/"
        self.env.update(KEYCLOAK_REALM="other", KEYCLOAK_CLIENT_ID="other-cli",
                        KEYCLOAK_USERNAME="other+user", KEYCLOAK_PASSWORD=password)
        result = self.request(201, json.dumps({"access_token": TOKEN}), trace=True)
        self.assertEqual((result.returncode, result.stdout), (0, TOKEN + "\n"))
        self.assertNotIn(password, result.stderr)
        self.assertNotIn(TOKEN, result.stderr)
        self.assertEqual(self.server.requests, [("/realms/other/protocol/openid-connect/token", {
            "grant_type": ["password"], "client_id": ["other-cli"],
            "username": ["other+user"], "password": [password],
        })])

    def test_bracketed_url_never_sends_credentials_to_expanded_destinations(self):
        base_url = self.env["KEYCLOAK_URL"]
        fixture_password = "fixture only + &"
        self.env.update(KEYCLOAK_URL=base_url + "/keycloak[1-2]", KEYCLOAK_PASSWORD=fixture_password)
        response = json.dumps({"access_token": TOKEN})
        result = self.request(200, response, trace=True)
        self.assertEqual([path for path, _ in self.server.requests], [])
        self.assert_rejected(result)
        self.assertNotIn(fixture_password, result.stderr)

        self.env["KEYCLOAK_URL"] = base_url + "/keycloak1"
        result = self.request(200, response)
        self.assertEqual((result.returncode, result.stdout), (0, TOKEN + "\n"))
        self.assertEqual([path for path, _ in self.server.requests], ["/keycloak1" + ENDPOINT])
        self.assertEqual(self.server.requests[0][1]["password"], [fixture_password])

    def test_rejects_urls_outside_the_absolute_http_base_url_form(self):
        base_url = self.env["KEYCLOAK_URL"]
        for url in (base_url.split("://", 1)[1], "/relative", "file:///tmp", "https:///missing-host",
                    base_url + "/{one,two}", base_url + "/a*b", base_url + "?query=1",
                    base_url + "#fragment", base_url + "/bad path", base_url + "/bad%"):
            with self.subTest(url=url):
                self.server.requests.clear()
                self.env["KEYCLOAK_URL"] = url
                result = self.request(200, json.dumps({"access_token": TOKEN}))
                self.assertEqual([path for path, _ in self.server.requests], [])
                self.assert_rejected(result)

    def test_curl_sends_literal_brackets_in_the_endpoint_path_once(self):
        self.env["KEYCLOAK_REALM"] = "fincore[1-2]"
        result = self.request(200, json.dumps({"access_token": TOKEN}))
        self.assertEqual([path for path, _ in self.server.requests],
                         ["/realms/fincore[1-2]/protocol/openid-connect/token"])
        self.assertEqual((result.returncode, result.stdout), (0, TOKEN + "\n"))

    def test_rejects_access_tokens_without_three_nonempty_base64url_segments(self):
        tokens = ("not:a:bearer", "opaque", "one.two", "one.two.three.four",
                  ".two.three", "one..three", "one.two.", "one.two+extra.three",
                  "one.two/extra.three", "one.two.three=")
        for token in tokens:
            with self.subTest(token=token):
                result = self.request(200, json.dumps({"access_token": token}))
                self.assert_rejected(result)
                self.assertNotIn(token, result.stderr)

    def test_rejects_blank_whitespace_and_control_characters_before_output(self):
        tokens = ("", " ", "\t", f" {TOKEN}", f"{TOKEN}\n", f"{TOKEN}\tmore",
                  f"{TOKEN}\r\nmore", f"{TOKEN}\u00a0more", f"{TOKEN}\u2003more",
                  f"{TOKEN}\0more")
        for token in tokens:
            with self.subTest(token=token):
                self.assert_rejected(self.request(200, json.dumps({"access_token": token})))

    def test_rejects_missing_non_string_and_malformed_responses(self):
        valid = json.dumps({"access_token": TOKEN})
        for body in ("{}", '{"access_token":null}', '{"access_token":123}', valid + " malformed",
                     valid + " {}"):
            with self.subTest(body=body):
                self.assert_rejected(self.request(200, body))

    def test_rejects_redirects_and_errors_without_printing_the_response(self):
        for status in (302, 401, 503):
            with self.subTest(status=status):
                self.assert_rejected(self.request(status, json.dumps({"access_token": TOKEN})))
        self.assertEqual([path for path, _ in self.server.requests], [ENDPOINT] * 3)


if __name__ == "__main__":
    unittest.main(verbosity=2)
