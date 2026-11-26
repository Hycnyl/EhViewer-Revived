// Copyright 2026 Hycnyl. SPDX-License-Identifier: GPL-3.0-or-later
package com.hippo.ehviewer.search

import com.ehviewer.core.model.TagNamespace

internal data class TagCatalogQuery(val namespace: TagNamespace?, val text: String) {
    fun matches(tag: TagIdentity, translation: String?): Boolean =
        (namespace == null || tag.namespace == namespace.value) &&
            (tag.name.contains(text, ignoreCase = true) || translation?.contains(text, ignoreCase = true) == true)
}
