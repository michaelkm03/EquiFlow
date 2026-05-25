import re
from datetime import date, timedelta

_UUID_RE = re.compile(
    r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",
    re.IGNORECASE,
)


def extract_uuid(text: str) -> str | None:
    m = _UUID_RE.search(text)
    return m.group(0) if m else None


def parse_date_range(question: str) -> tuple[str, str]:
    today = date.today()
    q = question.lower()
    if "yesterday" in q:
        d = today - timedelta(days=1)
        return str(d), str(d)
    if "this week" in q or "week" in q:
        monday = today - timedelta(days=today.weekday())
        return str(monday), str(today)
    if "this month" in q or "month" in q:
        return str(today.replace(day=1)), str(today)
    if "last 7 days" in q or "7 days" in q:
        return str(today - timedelta(days=7)), str(today)
    if "last 30 days" in q or "30 days" in q:
        return str(today - timedelta(days=30)), str(today)
    if "last hour" in q or "hour" in q:
        return str(today), str(today)
    return str(today), str(today)
