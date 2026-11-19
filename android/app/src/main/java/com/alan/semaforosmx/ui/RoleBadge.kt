package com.alan.semaforosmx.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alan.semaforosmx.R
import com.alan.semaforosmx.data.auth.UserRole
import com.alan.semaforosmx.data.auth.VerifiedBadge
import com.alan.semaforosmx.data.auth.verifiedBadge

/**
 * Sello de verificación junto al nombre (v1.1.1, SPEC §6.9).
 *
 * - ADMIN: palomita azul «Propietario» (solo Alan, dueño de la app).
 *   El rol ADMIN se asigna a mano con SQL; nadie puede ponérselo solo.
 * - COLLABORATOR: palomita verde «Colaborador verificado» (conductor
 *   con licencia de chofer verificada por el Admin).
 * - USER: no muestra nada.
 *
 * Es solo presentación: la separación real de permisos se hace cumplir
 * en Supabase con RLS, no con este sello.
 */
@Composable
fun RoleBadge(role: UserRole, modifier: Modifier = Modifier) {
    when (role.verifiedBadge()) {
        VerifiedBadge.OWNER -> BadgeRow(
            label = stringResource(R.string.badge_owner),
            description = stringResource(R.string.badge_owner_desc),
            color = Color(0xFF1D9BF0),
            modifier = modifier
        )
        VerifiedBadge.COLLABORATOR -> BadgeRow(
            label = stringResource(R.string.badge_collaborator),
            description = stringResource(R.string.badge_collaborator_desc),
            color = Color(0xFF2E9E5B),
            modifier = modifier
        )
        VerifiedBadge.NONE -> {
            // Usuario común: sin sello.
        }
    }
}

@Composable
private fun BadgeRow(
    label: String,
    description: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Verified,
            contentDescription = description,
            tint = color,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}
