/*
 * Copyright (C) 2026 Rainer Garbotz
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.richmarkdown.markdown;

import java.time.Instant;
import java.util.UUID;

/**
 * A single tracked change in a document.
 *
 * @param id          unique identifier
 * @param type        type of change (inserted, deleted, format changed)
 * @param startOffset start offset in the original document text
 * @param endOffset   end offset in the original document text
 * @param text        the inserted text (for INSERTED) or deleted text (for DELETED)
 * @param author      author of the change
 * @param timestamp   when the change was made
 * @param comment     optional comment on the change
 * @param status      current status of the change
 */
public record Change(
    String id,
    ChangeType type,
    int startOffset,
    int endOffset,
    String text,
    String author,
    Instant timestamp,
    String comment,
    ChangeStatus status
) {
    public Change {
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        if (author == null || author.isBlank()) {
            author = "Author";
        }
        if (timestamp == null) {
            timestamp = Instant.now();
        }
        if (status == null) {
            status = ChangeStatus.PENDING;
        }
    }

    /**
     * Creates a new change with updated status.
     */
    public Change withStatus(ChangeStatus newStatus) {
        return new Change(id, type, startOffset, endOffset, text, author, timestamp, comment, newStatus);
    }

    /**
     * Creates a new change with updated comment.
     */
    public Change withComment(String newComment) {
        return new Change(id, type, startOffset, endOffset, text, author, timestamp, newComment, status);
    }
}