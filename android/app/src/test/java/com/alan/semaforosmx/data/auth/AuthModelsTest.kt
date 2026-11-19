package com.alan.semaforosmx.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas de la lógica pura de autenticación y roles (v1.1, SPEC §4.7
 * y §6.9). Sin Android: corren con JUnitCore en el sandbox.
 */
class AuthModelsTest {

    private fun user(role: UserRole) = AppUser(
        id = "user-1",
        email = "alan@example.com",
        displayName = "Alan",
        photoUrl = null,
        role = role
    )

    // --- Roles ---

    @Test
    fun `fromString reconoce los tres roles`() {
        assertEquals(UserRole.USER, UserRole.fromString("USER"))
        assertEquals(UserRole.COLLABORATOR, UserRole.fromString("COLLABORATOR"))
        assertEquals(UserRole.ADMIN, UserRole.fromString("ADMIN"))
    }

    @Test
    fun `fromString desconocido o nulo es USER`() {
        assertEquals(UserRole.USER, UserRole.fromString("SUPERUSER"))
        assertEquals(UserRole.USER, UserRole.fromString(null))
        assertEquals(UserRole.USER, UserRole.fromString(""))
    }

    // --- Sesión ---

    @Test
    fun `isExpired detecta sesión vencida`() {
        val now = 1_000_000L
        val expired = AuthSession("a", "r", now - 1, "u", "e")
        assertTrue(expired.isExpired(now))
    }

    @Test
    fun `isExpired respeta el margen de 60 segundos`() {
        val now = 1_000_000L
        // Vence en 30 s: ya se considera vencida (margen de refresco).
        assertTrue(AuthSession("a", "r", now + 30_000L, "u", "e").isExpired(now))
        // Vence en 2 minutos: todavía válida.
        assertFalse(AuthSession("a", "r", now + 120_000L, "u", "e").isExpired(now))
    }

    @Test
    fun `toSession usa expires_at cuando viene del servidor`() {
        val response = SupabaseAuthApi.GoTrueSessionResponse(
            accessToken = "at",
            refreshToken = "rt",
            expiresIn = 3600,
            expiresAt = 1_800_000_000L,
            user = SupabaseAuthApi.GoTrueUser(id = "u1", email = "a@b.c")
        )
        val session = response.toSession(nowMillis = 0L)
        assertEquals(1_800_000_000_000L, session.expiresAtMillis)
        assertEquals("u1", session.userId)
        assertEquals("a@b.c", session.email)
    }

    @Test
    fun `toSession calcula el vencimiento desde expires_in si no hay expires_at`() {
        val response = SupabaseAuthApi.GoTrueSessionResponse(
            accessToken = "at",
            refreshToken = "rt",
            expiresIn = 3600,
            expiresAt = null,
            user = SupabaseAuthApi.GoTrueUser(id = "u1", email = null)
        )
        val session = response.toSession(nowMillis = 1_000_000L)
        assertEquals(1_000_000L + 3_600_000L, session.expiresAtMillis)
        assertEquals("", session.email)
    }

    // --- Puertas de permisos (RoleGate) ---

    @Test
    fun `en modo local todo está permitido sin login`() {
        assertTrue(RoleGate.canSyncFromWidget(null, supabaseConfigured = false))
        assertTrue(RoleGate.canCorrectMap(null, supabaseConfigured = false))
        assertTrue(RoleGate.canSeeContributions(null, supabaseConfigured = false))
    }

    @Test
    fun `con Supabase aportar exige login`() {
        assertFalse(RoleGate.canSyncFromWidget(null, supabaseConfigured = true))
        assertFalse(RoleGate.canCorrectMap(null, supabaseConfigured = true))
        assertFalse(RoleGate.canSeeContributions(null, supabaseConfigured = true))
        val u = user(UserRole.USER)
        assertTrue(RoleGate.canSyncFromWidget(u, supabaseConfigured = true))
        assertTrue(RoleGate.canCorrectMap(u, supabaseConfigured = true))
        assertTrue(RoleGate.canSeeContributions(u, supabaseConfigured = true))
    }

    @Test
    fun `modo prueba solo para Admin o con desbloqueo de desarrollador`() {
        assertTrue(RoleGate.canUseTestMode(user(UserRole.ADMIN), devUnlocked = false))
        assertTrue(RoleGate.canUseTestMode(user(UserRole.USER), devUnlocked = true))
        assertTrue(RoleGate.canUseTestMode(null, devUnlocked = true))
        assertFalse(RoleGate.canUseTestMode(user(UserRole.USER), devUnlocked = false))
        assertFalse(RoleGate.canUseTestMode(user(UserRole.COLLABORATOR), devUnlocked = false))
        assertFalse(RoleGate.canUseTestMode(null, devUnlocked = false))
    }

    @Test
    fun `AuthState refleja sesión y admin`() {
        val signedOut = AuthState()
        assertFalse(signedOut.isSignedIn)
        assertFalse(signedOut.isAdmin)
        val admin = AuthState(user = user(UserRole.ADMIN))
        assertTrue(admin.isSignedIn)
        assertTrue(admin.isAdmin)
        val collaborator = AuthState(user = user(UserRole.COLLABORATOR))
        assertTrue(collaborator.isSignedIn)
        assertFalse(collaborator.isAdmin)
    }

    // --- Sello de verificación (v1.1.1) ---

    @Test
    fun `Admin muestra la palomita azul de propietario`() {
        assertEquals(VerifiedBadge.OWNER, UserRole.ADMIN.verifiedBadge())
    }

    @Test
    fun `Colaborador muestra la palomita verde`() {
        assertEquals(VerifiedBadge.COLLABORATOR, UserRole.COLLABORATOR.verifiedBadge())
    }

    @Test
    fun `Usuario común no muestra sello`() {
        assertEquals(VerifiedBadge.NONE, UserRole.USER.verifiedBadge())
    }
}
