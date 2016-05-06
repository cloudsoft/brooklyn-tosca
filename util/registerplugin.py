#!/usr/bin/env python
try:
    import requests
except ImportError:
    exit("Install the requests package to use this script: pip install requests")

import logging
import os
import sys
import time


logging.basicConfig(level=logging.INFO, format='%(levelname)s %(message)s')
logging.getLogger('requests').setLevel(logging.WARNING)


def envvar(var, default):
    return os.environ[var] if var in os.environ else default


ROOT_URL = envvar("ALIEN_URL", "http://127.0.0.1:8091")
USERNAME = envvar("ALIEN_USER", "admin")
PASSWORD = envvar("ALIEN_PASSWORD", "admin")
SERVER_TIMEOUT = envvar("TIMEOUT", 120)
BROOKLYN_PLUGIN_NAME = envvar("BROOKLYN_PLUGIN_NAME", "a4c-brooklyn-provider")


if "BROOKLYN_PLUGIN" in os.environ:
    BROOKLYN_PLUGIN_FILE = os.path.abspath(os.environ["BROOKLYN_PLUGIN"])
else:
    scriptDir = os.path.dirname(sys.argv[0])
    guess = scriptDir + "/../a4c-brooklyn-plugin/target/a4c-brooklyn-plugin-0.10.0-SNAPSHOT.zip"
    BROOKLYN_PLUGIN_FILE = os.path.abspath(guess)

if not os.path.exists(BROOKLYN_PLUGIN_FILE):
    exit("Cannot find plugin at " + BROOKLYN_PLUGIN_FILE)


class Plugin:
    def __init__(self, data):
        self.__dict__ = data


class Plugins:
    def __init__(self, session):
        self.session = session
        self.url = ROOT_URL + "/rest/plugins"

    def list(self):
        return map(Plugin, self.session.get(self.url).json()['data']['data'])

    def create(self, payload):
        if not os.path.exists(payload):
            raise Exception("No file at: " + BROOKLYN_PLUGIN_FILE)
        logging.info("Registering plugin " + payload)
        files = {'file': open(payload, 'rb')}
        return self.session.post(self.url, files=files)

    def delete(self, id):
        logging.info("Deleting plugin " + id)
        return self.session.delete(self.url + "/" + id)


def newSession():
    """:return: a new session with a JSESSIONID cookie set."""
    s = requests.Session()
    s.get(ROOT_URL)
    return s


def signIn(session):
    auth = {
        "username": USERNAME,
        "password": PASSWORD
    }
    r = s.post(ROOT_URL + "/login", data=auth)
    if (r.status_code / 100) != 2:
        raise Exception("Couldn't sign in to server with given credentials. " +
                "Server responded: %d" % (r.status_code,))


def waitForServerReady():
    maxWait = SERVER_TIMEOUT
    wait = 0
    logged = False
    while wait < maxWait:
        try:
            requests.get(ROOT_URL)
            if logged:
                logging.info("Server ready")
            return
        except:
            if not logged:
                logging.info("Waiting up to %d seconds for A4C server at %s to respond" % (maxWait, ROOT_URL))
                logged = True
            wait += 1
            time.sleep(1)
    raise Exception("Timed out waiting for server at %s to respond" % (ROOT_URL,))


def refreshBrooklynPlugin(session):
    api = Plugins(s)
    # Delete existing
    for plugin in api.list():
        if plugin.descriptor['id'] == BROOKLYN_PLUGIN_NAME:
            api.delete(plugin.id)
    api.create(BROOKLYN_PLUGIN_FILE)


if __name__ == "__main__":
    waitForServerReady()
    s = newSession()
    signIn(s)
    refreshBrooklynPlugin(s)

