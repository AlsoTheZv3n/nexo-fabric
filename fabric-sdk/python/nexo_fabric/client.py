from __future__ import annotations
import httpx
from typing import Any, Optional


class FabricClient:
    def __init__(self, base_url: str, api_key: str | None = None):
        self.base_url = base_url.rstrip("/")
        headers = {"Content-Type": "application/json"}
        if api_key:
            headers["Authorization"] = f"Bearer {api_key}"
        self._http = httpx.Client(headers=headers, timeout=30)

    # Schema
    def list_object_types(self) -> list[dict]:
        return self._gql(
            "{ getAllObjectTypes { id apiName displayName properties { apiName dataType } } }"
        )["getAllObjectTypes"]

    # Objects
    def search_objects(self, object_type: str, limit: int = 20) -> dict:
        return self._gql(
            "query($t:String!,$p:PaginationInput){searchObjects(objectType:$t,pagination:$p){items{id objectType properties createdAt}totalCount}}",
            {"t": object_type, "p": {"limit": limit}},
        )["searchObjects"]

    def create_object(self, object_type: str, properties: dict) -> dict:
        return self._gql(
            "mutation($t:String!,$p:JSON!){createObject(objectType:$t,properties:$p){id objectType properties}}",
            {"t": object_type, "p": properties},
        )["createObject"]

    # Semantic search
    def semantic_search(self, query: str, object_type: str, limit: int = 10) -> list[dict]:
        return self._gql(
            "query($q:String!,$t:String!,$l:Int){semanticSearch(query:$q,objectType:$t,limit:$l){similarity object{id properties}}}",
            {"q": query, "t": object_type, "l": limit},
        )["semanticSearch"]

    # Agent
    def ask(self, message: str, session_id: str | None = None) -> dict:
        return self._gql(
            "mutation($m:String!,$s:ID){agentChat(message:$m,sessionId:$s){message sessionId}}",
            {"m": message, "s": session_id},
        )["agentChat"]

    # Events
    def send_event(self, source_system: str, payload: dict) -> dict:
        r = self._http.post(f"{self.base_url}/api/v1/events/inbound/{source_system}", json=payload)
        r.raise_for_status()
        return r.json()

    # Internal
    def _gql(self, query: str, variables: dict | None = None) -> dict:
        r = self._http.post(f"{self.base_url}/graphql", json={"query": query, "variables": variables or {}})
        r.raise_for_status()
        data = r.json()
        if "errors" in data:
            raise Exception(data["errors"][0]["message"])
        return data["data"]

    def close(self):
        self._http.close()

    def __enter__(self):
        return self

    def __exit__(self, *_):
        self.close()
