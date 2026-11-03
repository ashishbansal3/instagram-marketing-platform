import { NextRequest, NextResponse } from "next/server";
import {
  createOnboardingFromFormData,
  getOnboardingStatus,
} from "@/application/onboarding-service";
import { getCorrelationId, CORRELATION_HEADER } from "@/lib/correlation";
import { AppError, statusFor, toErrorBody } from "@/lib/errors";
import { env } from "@/config/env";

export async function POST(req: NextRequest) {
  const correlationId = getCorrelationId(req);
  try {
    const contentType = req.headers.get("content-type") || "";
    const body = await req.arrayBuffer();
    // Re-parse FormData from a clone of the buffer
    const formReq = new Request(req.url, {
      method: "POST",
      headers: contentType ? { "content-type": contentType } : undefined,
      body,
    });
    const form = await formReq.formData();

    const { record, source } = await createOnboardingFromFormData(
      form,
      correlationId,
    );

    return NextResponse.json(
      {
        id: record.id,
        status: record.status,
        source,
        websitePreviewUrl: record.websitePreviewUrl ?? null,
        websiteStatus: record.websiteStatus ?? null,
        setupJobId: record.setupJobId,
      },
      {
        status: 201,
        headers: { [CORRELATION_HEADER]: correlationId },
      },
    );
  } catch (err) {
    return NextResponse.json(toErrorBody(err, correlationId), {
      status: statusFor(err),
      headers: { [CORRELATION_HEADER]: correlationId },
    });
  }
}

export async function GET(req: NextRequest) {
  const correlationId = getCorrelationId(req);
  try {
    const id = req.nextUrl.searchParams.get("id");
    if (!id) {
      throw new AppError("VALIDATION_ERROR", "id query parameter is required", 400);
    }

    if (env.springApiUrl) {
      try {
        const upstream = await fetch(
          `${env.springApiUrl}/api/v1/onboarding/${encodeURIComponent(id)}/status`,
          { headers: { [CORRELATION_HEADER]: correlationId } },
        );
        const text = await upstream.text();
        return new NextResponse(text, {
          status: upstream.status,
          headers: {
            "content-type":
              upstream.headers.get("content-type") || "application/json",
            [CORRELATION_HEADER]: correlationId,
          },
        });
      } catch {
        /* fall through to local */
      }
    }

    const record = getOnboardingStatus(id);
    return NextResponse.json(record, {
      headers: { [CORRELATION_HEADER]: correlationId },
    });
  } catch (err) {
    return NextResponse.json(toErrorBody(err, correlationId), {
      status: statusFor(err),
      headers: { [CORRELATION_HEADER]: correlationId },
    });
  }
}
