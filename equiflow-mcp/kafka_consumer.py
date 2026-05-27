"""
Background Kafka consumer — triggers the escalation agent when a saga fails.

Listens on:
  equiflow.saga.failed       — saga reached FAILED status
  equiflow.saga.compensated  — saga completed compensation rollback

Each message is expected to contain {"orderId": "<UUID>"}.

Run: python equiflow-mcp/kafka_consumer.py
"""
import json
import subprocess
import sys

from kafka import KafkaConsumer

BOOTSTRAP_SERVERS = "localhost:9092"
TOPICS = ["equiflow.saga.failed", "equiflow.saga.compensated"]


def main():
    consumer = KafkaConsumer(
        *TOPICS,
        bootstrap_servers=BOOTSTRAP_SERVERS,
        value_deserializer=lambda m: json.loads(m.decode("utf-8")),
        auto_offset_reset="latest",
        group_id="equiflow-escalation-agent",
    )

    print(f"Listening on {TOPICS} at {BOOTSTRAP_SERVERS} ...")

    for message in consumer:
        order_id = message.value.get("orderId")
        if not order_id:
            print(f"Skipping message with no orderId: {message.value}", file=sys.stderr)
            continue

        print(f"[{message.topic}] Received saga event for order {order_id} — triggering escalation agent")
        subprocess.run(
            [sys.executable, "escalation_agent.py", order_id],
            cwd="equiflow-mcp",
        )


if __name__ == "__main__":
    main()
