import anthropic
from typing import Any, Callable, Coroutine


async def run_agent(
    system: str,
    tools: list[dict],
    call_tool_fn: Callable[[str, dict], Coroutine[Any, Any, str]],
    question: str,
    max_iterations: int = 10,
) -> str:
    client = anthropic.Anthropic()
    messages = [{"role": "user", "content": question}]

    for iteration in range(1, max_iterations + 1):
        print(f"\n[iteration {iteration}] sending to model...")
        response = client.messages.create(
            model="claude-opus-4-7",
            max_tokens=4096,
            system=system,
            tools=tools,
            messages=messages,
        )
        print(f"[iteration {iteration}] stop_reason={response.stop_reason}")

        if response.stop_reason == "end_turn":
            return next((b.text for b in response.content if hasattr(b, "text")), "")

        if response.stop_reason == "max_tokens":
            return "Agent stopped: response exceeded token limit."

        if response.stop_reason == "tool_use":
            messages.append({"role": "assistant", "content": response.content})

            tool_results = []
            for block in response.content:
                if block.type == "tool_use":
                    print(f"  >> {block.name}({block.input})")
                    result = await call_tool_fn(block.name, block.input)
                    print(f"  << {result}")
                    tool_results.append({
                        "type": "tool_result",
                        "tool_use_id": block.id,
                        "content": result,
                    })

            messages.append({"role": "user", "content": tool_results})

    return "Agent did not finish within the iteration limit."
