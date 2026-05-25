from typing import Any, AsyncGenerator, Callable, Coroutine


async def run_agent_local(
    planner_fn,
    call_tool_fn: Callable[[str, dict], Coroutine[Any, Any, str]],
    question: str,
) -> AsyncGenerator[dict, None]:
    async for event in planner_fn(question, call_tool_fn):
        yield event
