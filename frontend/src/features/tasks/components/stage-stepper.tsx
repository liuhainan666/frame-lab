"use client";

import { useI18n } from "@/i18n/provider";
import { cn } from "@/shared/lib/cn";

import { stageLabelKey } from "../status";
import type { TaskStage, TaskStatus, TaskStep } from "../types";

const STAGES: TaskStage[] = ["MATERIAL_CHECK", "GENERATE", "FINALIZE"];

type NodeState = "pending" | "active" | "done" | "failed" | "skipped";

function stateFor(
  stage: TaskStage,
  index: number,
  currentStage: TaskStage | null,
  status: TaskStatus,
  steps?: TaskStep[]
): NodeState {
  if (steps) {
    const step = steps.find((item) => item.stage === stage);
    if (!step) {
      return "pending";
    }
    switch (step.status) {
      case "SUCCEEDED":
        return "done";
      case "FAILED":
        return "failed";
      case "SKIPPED":
        return "skipped";
      case "RUNNING":
        return "active";
      default:
        return "pending";
    }
  }

  const currentIndex = currentStage ? STAGES.indexOf(currentStage) : -1;
  if (index < currentIndex) {
    return "done";
  }
  if (index === currentIndex) {
    return status === "FAILED" ? "failed" : status === "RUNNING" ? "active" : "pending";
  }
  return "pending";
}

export function StageStepper({
  currentStage,
  status,
  steps,
  className
}: {
  currentStage: TaskStage | null;
  status: TaskStatus;
  steps?: TaskStep[];
  className?: string;
}) {
  const { t } = useI18n();
  const labelKey = stageLabelKey(currentStage);

  return (
    <div className={cn("flex flex-col gap-2", className)} data-testid="stage-stepper">
      <div className="flex items-center">
        {STAGES.map((stage, index) => {
          const state = stateFor(stage, index, currentStage, status, steps);
          return (
            <div key={stage} className="flex flex-1 items-center last:flex-none">
              <span
                aria-hidden
                className={cn(
                  "h-1.5 w-1.5 shrink-0 rounded-full",
                  state === "done" && "bg-success",
                  state === "active" && "bg-accent motion-safe:animate-[breathe_1.2s_ease-in-out_infinite]",
                  state === "failed" && "bg-danger",
                  state === "pending" && "bg-line-strong",
                  state === "skipped" && "bg-line"
                )}
              />
              {index < STAGES.length - 1 ? (
                <span
                  aria-hidden
                  className={cn(
                    "mx-1.5 h-px flex-1",
                    state === "done" ? "bg-success/40" : "bg-line-strong"
                  )}
                />
              ) : null}
            </div>
          );
        })}
      </div>
      {labelKey ? <p className="text-[11px] text-ink-muted">{t(labelKey)}</p> : null}
    </div>
  );
}
