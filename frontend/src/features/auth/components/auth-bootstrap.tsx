"use client";

import { useEffect } from "react";

import { bootstrapSession } from "@/features/auth/api";

export function AuthBootstrap() {
  useEffect(() => {
    void bootstrapSession();
  }, []);
  return null;
}
