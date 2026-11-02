import { Card } from "antd";

export function PendingModulePage({ title, phase, summary }: { title: string; phase: string; summary: string }) {
  return (
    <Card title={title}>
      <p style={{ marginTop: 0 }}>{phase}</p>
      <p>{summary}</p>
    </Card>
  );
}
