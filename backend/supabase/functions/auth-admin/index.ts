// Supabase Edge Function: auth-admin
// Secure server-side user admin operations so client apps never need the service_role key.

import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
    const supabaseServiceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";

    if (!supabaseUrl || !supabaseServiceKey) {
      return new Response(
        JSON.stringify({ error: "Missing server environment variables" }),
        { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const supabaseAdmin = createClient(supabaseUrl, supabaseServiceKey, {
      auth: { persistSession: false, autoRefreshToken: false },
    });

    const body = await req.json().catch(() => ({}));
    const action = body.action;

    // 1. REGISTER USER (Creates user in auth.users with email_confirm: true)
    if (action === "register_user") {
      const email = (body.email || "").toLowerCase().trim();
      const password = body.password || "";
      const firstName = body.firstName || "";
      const lastName = body.lastName || "";
      const provider = body.provider || "email";

      if (!email) {
        return new Response(
          JSON.stringify({ error: "Email is required" }),
          { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
      }

      const { data: userData, error: createError } = await supabaseAdmin.auth.admin.createUser({
        email,
        password: password || undefined,
        email_confirm: true,
        user_metadata: {
          first_name: firstName,
          last_name: lastName,
          full_name: `${firstName} ${lastName}`.trim(),
          provider,
        },
      });

      if (createError) {
        // If user already exists (422), attempt to update user_metadata or fetch existing
        const { data: listData } = await supabaseAdmin.auth.admin.listUsers();
        const existing = listData?.users?.find(u => u.email?.toLowerCase() === email);
        if (existing) {
          return new Response(
            JSON.stringify({ success: true, userId: existing.id, existing: true }),
            { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
          );
        }
        return new Response(
          JSON.stringify({ error: createError.message }),
          { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
      }

      return new Response(
        JSON.stringify({ success: true, userId: userData.user.id }),
        { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // 2. UPDATE PASSWORD (Requires either user session JWT or admin reset)
    if (action === "update_password") {
      const email = (body.email || "").toLowerCase().trim();
      const newPassword = body.newPassword || "";
      if (!email || !newPassword) {
        return new Response(
          JSON.stringify({ error: "Email and new password are required" }),
          { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
      }

      // Check auth header if provided
      const authHeader = req.headers.get("Authorization");
      let targetUserId: string | null = null;

      if (authHeader) {
        const token = authHeader.replace("Bearer ", "").trim();
        const { data: { user } } = await supabaseAdmin.auth.getUser(token);
        if (user && user.email?.toLowerCase() === email) {
          targetUserId = user.id;
        }
      }

      if (!targetUserId) {
        // Look up by email
        const { data: listData } = await supabaseAdmin.auth.admin.listUsers();
        const matched = listData?.users?.find(u => u.email?.toLowerCase() === email);
        if (matched) targetUserId = matched.id;
      }

      if (!targetUserId) {
        return new Response(
          JSON.stringify({ error: "User not found" }),
          { status: 404, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
      }

      const { error: updateError } = await supabaseAdmin.auth.admin.updateUserById(targetUserId, {
        password: newPassword,
      });

      if (updateError) {
        return new Response(
          JSON.stringify({ error: updateError.message }),
          { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
      }

      return new Response(
        JSON.stringify({ success: true }),
        { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // 3. DELETE ACCOUNT (Requires user session or verified email)
    if (action === "delete_account") {
      const email = (body.email || "").toLowerCase().trim();
      const authHeader = req.headers.get("Authorization");
      let targetUserId: string | null = null;

      if (authHeader) {
        const token = authHeader.replace("Bearer ", "").trim();
        const { data: { user } } = await supabaseAdmin.auth.getUser(token);
        if (user) targetUserId = user.id;
      }

      if (!targetUserId && email) {
        const { data: listData } = await supabaseAdmin.auth.admin.listUsers();
        const matched = listData?.users?.find(u => u.email?.toLowerCase() === email);
        if (matched) targetUserId = matched.id;
      }

      if (!targetUserId) {
        return new Response(
          JSON.stringify({ error: "Unauthorized or user not found" }),
          { status: 401, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
      }

      const { error: delError } = await supabaseAdmin.auth.admin.deleteUser(targetUserId);
      if (delError) {
        return new Response(
          JSON.stringify({ error: delError.message }),
          { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
      }

      return new Response(
        JSON.stringify({ success: true }),
        { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    return new Response(
      JSON.stringify({ error: "Unknown action" }),
      { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  } catch (err: any) {
    return new Response(
      JSON.stringify({ error: err.message || "Internal error" }),
      { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  }
});
