import os
from pathlib import Path
from typing import Any, AsyncGenerator, Callable, Coroutine

import anthropic

_env_file = Path(__file__).parent.parent / ".env"
if _env_file.exists() and not os.environ.get("ANTHROPIC_API_KEY"):
    for line in _env_file.read_text().splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            k, _, v = line.partition("=")
            os.environ.setdefault(k.strip(), v.strip())


async def run_agent_streaming(
    system: str,
    tools: list[dict],
    call_tool_fn: Callable[[str, dict], Coroutine[Any, Any, str]],
    question: str,
    max_iterations: int = 10,
) -> AsyncGenerator[dict, None]:
    """
    Streaming variant of run_agent. Yields typed event dicts:
      { type: "iteration_start", iteration: int }
      { type: "tool_call",       name: str, input: dict }
      { type: "tool_result",     name: str, result: str }
      { type: "token_usage",     iteration: int, input_tokens: int, output_tokens: int }
      { type: "done",            answer: str }
      { type: "error",           message: str }
    """
    client = anthropic.AsyncAnthropic()
    messages = [{"role": "user", "content": question}]

    for iteration in range(1, max_iterations + 1):
        yield {"type": "iteration_start", "iteration": iteration}

        async with client.messages.stream(
            model="claude-opus-4-7",
            max_tokens=32000,
            system=system,
            tools=tools,
            messages=messages,
        ) as stream:
            response = await stream.get_final_message()

        yield {
            "type": "token_usage",
            "iteration": iteration,
            "input_tokens": response.usage.input_tokens,
            "output_tokens": response.usage.output_tokens,
        }

        if response.stop_reason == "end_turn":
            answer = next((b.text for b in response.content if hasattr(b, "text")), "")
            yield {"type": "done", "answer": answer}
            return

        if response.stop_reason == "max_tokens":
            yield {"type": "error", "message": "Agent stopped: response exceeded token limit."}
            return

        if response.stop_reason == "tool_use":
            messages.append({"role": "assistant", "content": response.content})

            tool_results = []
            for block in response.content:
                if block.type == "tool_use":
                    yield {"type": "tool_call", "name": block.name, "input": block.input}
                    result = await call_tool_fn(block.name, block.input)
                    yield {"type": "tool_result", "name": block.name, "result": result}
                    tool_results.append({
                        "type": "tool_result",
                        "tool_use_id": block.id,
                        "content": result,
                    })

            messages.append({"role": "user", "content": tool_results})

    yield {"type": "error", "message": "Agent did not finish within the iteration limit."}
