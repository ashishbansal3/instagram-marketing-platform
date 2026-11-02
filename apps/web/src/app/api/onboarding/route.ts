import { NextRequest, NextResponse } from "next/server";
import { randomUUID } from "crypto";
import { getOnboarding, saveOnboarding } from "@/lib/mock-store";

const SPRING_API_URL = process.env.SPRING_API_URL?.replace(/\/$/, "");

export async function POST(req: NextRequest) {
  const contentType = req.headers.get("content-type") || "";

  // Prefer Spring when configured — forward multipart as-is.
  if (SPRING_API_URL) {
    try {
      const body = await req.arrayBuffer();
      const upstream = await fetch(`${SPRING_API_URL}/api/onboarding`, {
        method: "POST",
        headers: contentType ? { "content-type": contentType } : undefined,
        body,
      });
      const text = await upstream.text();
      return new NextResponse(text, {
        status: upstream.status,
        headers: {
          "content-type":
            upstream.headers.get("content-type") || "application/json",
        },
      });
    } catch {
      // Fall through to demo mock if Spring unreachable.
    }
  }

  const form = await req.formData();
  const phone = String(form.get("phone") || "").trim();
  const businessName = String(form.get("businessName") || "").trim();
  const category = String(form.get("category") || "").trim();
  const city = String(form.get("city") || "").trim();
  const bio = String(form.get("bio") || "").trim();
  const photo = form.get("photo");

  if (!phone || !businessName || !category || !city || !bio) {
    return NextResponse.json(
      { error: "phone, businessName, category, city, and bio are required" },
      { status: 400 },
    );
  }

  const photoName =
    photo && typeof photo === "object" && "name" in photo && photo.size > 0
      ? String(photo.name)
      : null;

  const record = saveOnboarding({
    id: randomUUID(),
    phone,
    businessName,
    category,
    city,
    bio,
    photoName,
    status: "profile_collected",
    createdAt: new Date().toISOString(),
  });

  return NextResponse.json({
    id: record.id,
    status: record.status,
    demo: !SPRING_API_URL,
  });
}

export async function GET(req: NextRequest) {
  const id = req.nextUrl.searchParams.get("id");
  if (!id) {
    return NextResponse.json({ error: "id required" }, { status: 400 });
  }

  if (SPRING_API_URL) {
    try {
      const upstream = await fetch(
        `${SPRING_API_URL}/api/onboarding/${encodeURIComponent(id)}/status`,
      );
      const text = await upstream.text();
      return new NextResponse(text, {
        status: upstream.status,
        headers: {
          "content-type":
            upstream.headers.get("content-type") || "application/json",
        },
      });
    } catch {
      /* mock fallback */
    }
  }

  const record = getOnboarding(id);
  if (!record) {
    return NextResponse.json({ error: "not found" }, { status: 404 });
  }
  return NextResponse.json(record);
}
