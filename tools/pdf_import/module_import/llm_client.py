import os
from pathlib import Path

import requests
from dotenv import load_dotenv


API_URL = "https://llmapi-key.ris.bht-berlin.de/v1/chat/completions"
MODEL_NAME = "bht/large"
ENV_PATH = Path(__file__).resolve().parents[1] / ".env"

load_dotenv(ENV_PATH)


def call_llm(prompt: str) -> str:
    """
    Call BHT RIS LLM API with API-key authentication.

    The API key is read from:
    tools/pdf_import/.env

    Expected variable:
    BHT_LLM_API_KEY
    """

    api_key = os.getenv("BHT_LLM_API_KEY")

    if not api_key:
        raise RuntimeError(
            "Missing BHT_LLM_API_KEY. Set your API key in tools/pdf_import/.env."
        )

    headers = {
        "x-api-key": api_key,
        "Content-Type": "application/json",
    }

    payload = {
        "model": MODEL_NAME,
        "messages": [
            {
                "role": "user",
                "content": prompt,
            }
        ],
        "temperature": 0.0,
    }

    response = requests.post(
        API_URL,
        headers=headers,
        json=payload,
        timeout=180,
    )

    if response.status_code != 200:
        raise RuntimeError(
            f"LLM API error {response.status_code}: {response.text}"
        )

    data = response.json()

    return data["choices"][0]["message"]["content"]


if __name__ == "__main__":
    result = call_llm('Return JSON only: {"message": "hello"}')
    print(result)