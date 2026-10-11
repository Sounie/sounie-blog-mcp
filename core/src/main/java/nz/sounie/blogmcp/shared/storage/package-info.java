/**
 * File storage helpers shared by both contexts' outbound adapters: atomic file writes, temporary
 * file sweeping, quarantine of unreadable files, and safe file names. JDK types only; depends on no
 * bounded context (ADR 0007).
 */
package nz.sounie.blogmcp.shared.storage;
