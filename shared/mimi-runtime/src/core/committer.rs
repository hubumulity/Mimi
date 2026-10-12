//! Tracks the latest cumulative ASR draft and derives replaceable preview
//! candidates without advancing any durable subtitle boundary.

pub struct ASRDraftCommitter {
    long_incomplete_commit_threshold: usize,
    latest_draft: String,
    /// A corroborated display boundary in `latest_draft`, never consumed text
    /// or a provider-final boundary. Only the one bounded draft is retained.
    stable_prefix_end: usize,
    /// Progress admitted to the preview HTTP lane. A correction can shrink
    /// this to an interior UTF-8 byte; it is compared only, never used to slice.
    previewed_end: usize,
}

impl ASRDraftCommitter {
    pub fn new(long_incomplete_commit_threshold: usize) -> Self {
        Self {
            long_incomplete_commit_threshold: long_incomplete_commit_threshold.max(1),
            latest_draft: String::new(),
            stable_prefix_end: 0,
            previewed_end: 0,
        }
    }

    pub fn has_pending_text(&self) -> bool {
        Self::is_meaningful(&self.latest_draft)
    }

    /// Returns the whole latest draft without making it durable. When
    /// `require_long` is true, short incomplete speech stays pending until it
    /// grows or the provider supplies an authoritative final.
    pub fn preview_latest_draft(&self, require_long: bool) -> Option<String> {
        if !Self::is_meaningful(&self.latest_draft) {
            return None;
        }
        if require_long && self.latest_draft.chars().count() < self.long_incomplete_commit_threshold
        {
            return None;
        }
        Some(self.latest_draft.clone())
    }

    /// Prefer a complete prefix whose text and sentence boundary survived two
    /// consecutive normalized ASR drafts. This is still a replaceable preview;
    /// the provider can revise it later, and no tail is consumed or confirmed.
    /// Only a boundary beyond already-started preview work makes progress.
    /// A caller's bounded-wait rescue uses the whole draft when no new stable
    /// boundary exists, so a previously previewed prefix cannot starve its tail.
    pub fn preview_readable_draft(&self, allow_incomplete: bool) -> Option<String> {
        if self.stable_prefix_end > self.previewed_end {
            let prefix = &self.latest_draft[..self.stable_prefix_end];
            if Self::is_meaningful(prefix) {
                return Some(prefix.to_string());
            }
        }
        allow_incomplete
            .then(|| self.preview_latest_draft(false))
            .flatten()
    }

    /// Record an exact source candidate only when its HTTP request starts, not
    /// while a replaceable waiter is scheduled. A candidate revised away by
    /// ASR cannot claim progress in this latest draft. Repeated or shorter
    /// candidates never move an existing progress boundary backwards.
    pub fn note_preview_started(&mut self, text: &str) -> bool {
        if !Self::is_meaningful(text) || !self.latest_draft.starts_with(text) {
            return false;
        }
        self.previewed_end = self.previewed_end.max(text.len());
        true
    }

    /// A started complete-prefix request can still lose its sentence boundary
    /// while keeping identical prefix bytes, for example "3." becoming
    /// "3.14". Check both the exact text and the current full boundary shape
    /// before publishing that request's result. Rescue drafts use a separate
    /// caller policy because they never claimed a complete sentence boundary.
    pub fn retains_complete_prefix(&self, text: &str) -> bool {
        Self::is_meaningful(text)
            && self.latest_draft.starts_with(text)
            && corroborated_complete_prefix_end(text, &self.latest_draft, text.len()) == text.len()
    }

    /// Replaces the cumulative provider draft and returns its normalized text.
    pub fn update_draft(&mut self, text: &str) -> String {
        if !crate::core::models::subtitle_text_within_limit(text) {
            return self.latest_draft.clone();
        }
        let text = text.trim();
        let common_bytes = common_prefix_bytes(&self.latest_draft, text);
        self.previewed_end = self.previewed_end.min(common_bytes);
        self.stable_prefix_end =
            corroborated_complete_prefix_end(&self.latest_draft, text, common_bytes);
        self.latest_draft = text.to_string();
        self.latest_draft.clone()
    }

    pub fn reset(&mut self) {
        self.latest_draft.clear();
        self.stable_prefix_end = 0;
        self.previewed_end = 0;
    }

    /// Text is meaningful when it contains a non-whitespace, non-punctuation
    /// character.
    fn is_meaningful(text: &str) -> bool {
        text.chars()
            .any(|character| !character.is_whitespace() && !is_punctuation(character))
    }
}

fn common_prefix_bytes(previous: &str, current: &str) -> usize {
    previous
        .bytes()
        .zip(current.bytes())
        .take_while(|(previous, current)| previous == current)
        .count()
}

fn corroborated_complete_prefix_end(previous: &str, current: &str, common_bytes: usize) -> usize {
    let mut index = 0;
    let mut stable_end = 0;
    while index < common_bytes {
        let character = current[index..].chars().next().unwrap();
        let after = index + character.len_utf8();
        // The common byte count can end inside a changed UTF-8 character.
        if after > common_bytes {
            break;
        }
        if matches!(character, '.' | '!' | '?' | '。' | '！' | '？') {
            let end = sentence_tail_end(current, after, common_bytes);
            if sentence_boundary_end(current, index, character, end) == Some(end)
                && sentence_boundary_end(previous, index, character, end) == Some(end)
            {
                stable_end = end;
            }
            // Each emphasis/closer run is visited as a group, not rescanned
            // from every punctuation character in a maximum-sized draft.
            index = end;
        } else {
            index = after;
        }
    }
    stable_end
}

fn sentence_boundary_end(
    text: &str,
    index: usize,
    character: char,
    maximum_end: usize,
) -> Option<usize> {
    if !matches!(character, '.' | '!' | '?' | '。' | '！' | '？') {
        return None;
    }
    let after = index + character.len_utf8();
    let end = sentence_tail_end(text, after, maximum_end);
    // Inspect one following character in the complete draft: a run clipped
    // by the common-prefix limit is not an observed complete boundary.
    if text[end..].chars().next().is_some_and(is_sentence_tail)
        || (character == '.' && !is_sentence_period(text, index, after))
    {
        return None;
    }
    Some(end)
}

fn sentence_tail_end(text: &str, after: usize, maximum_end: usize) -> usize {
    let mut end = after;
    // Keep emphasis punctuation and closing quotes attached to the sentence.
    // A newly arrived closer must itself survive another draft before use.
    for next in text[end..].chars() {
        if is_sentence_tail(next) && end + next.len_utf8() <= maximum_end {
            end += next.len_utf8();
        } else {
            break;
        }
    }
    end
}

fn is_sentence_tail(character: char) -> bool {
    matches!(character, '!' | '?' | '！' | '？') || is_sentence_closer(character)
}

fn is_sentence_period(text: &str, index: usize, end: usize) -> bool {
    let before = &text[..index];
    let after = &text[end..];
    // Decimal, URL/domain and ellipsis interiors are not sentence boundaries.
    // Requiring whitespace after any closers also excludes an unfinished
    // ASCII word extension such as "example.com".
    if before.ends_with(['.', '…'])
        || after.starts_with('.')
        || after
            .chars()
            .find(|character| !is_sentence_closer(*character))
            .is_some_and(|character| !character.is_whitespace())
    {
        return false;
    }
    let token_start = before
        .char_indices()
        .rev()
        .find(|(_, character)| !character.is_alphanumeric() && *character != '.')
        .map_or(0, |(index, character)| index + character.len_utf8());
    let token = &before[token_start..];
    if token.len() == 1 && token.as_bytes()[0].is_ascii_alphabetic() {
        return false;
    }
    if token.contains('.')
        && token
            .split('.')
            .all(|part| part.len() == 1 && part.as_bytes()[0].is_ascii_alphabetic())
    {
        return false;
    }
    !matches!(
        token.to_ascii_lowercase().as_str(),
        "mr" | "mrs"
            | "ms"
            | "mx"
            | "dr"
            | "prof"
            | "sr"
            | "jr"
            | "st"
            | "vs"
            | "etc"
            | "e.g"
            | "i.e"
            | "ph.d"
            | "a.m"
            | "p.m"
            | "approx"
            | "dept"
            | "fig"
            | "inc"
            | "ltd"
            | "corp"
            | "co"
            | "mt"
    )
}

fn is_sentence_closer(character: char) -> bool {
    matches!(
        character,
        '\'' | '"' | '’' | '”' | '»' | '›' | '」' | '』' | ')' | '）' | ']' | '】' | '}'
    )
}

fn is_punctuation(character: char) -> bool {
    !character.is_alphanumeric() && !character.is_whitespace() && !character.is_control()
        || character.is_ascii_punctuation()
        || matches!(
            character,
            '。' | '、'
                | '！'
                | '？'
                | '「'
                | '」'
                | '『'
                | '』'
                | '（'
                | '）'
                | '・'
                | '…'
                | '—'
                | '–'
        )
}

impl Default for ASRDraftCommitter {
    fn default() -> Self {
        Self::new(20)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn oversized_draft_keeps_the_previous_complete_candidate_and_exact_utf8_is_accepted() {
        let mut committer = ASRDraftCommitter::default();
        committer.update_draft("Synthetic previous candidate");
        let exact = "🙂".repeat(crate::core::models::MAX_SUBTITLE_TEXT_BYTES / 4);
        assert_eq!(
            committer.update_draft(&format!("{exact}a")),
            "Synthetic previous candidate"
        );
        assert_eq!(
            committer.preview_latest_draft(false).as_deref(),
            Some("Synthetic previous candidate")
        );
        assert_eq!(committer.update_draft(&exact), exact);
    }

    #[test]
    fn keeps_an_incomplete_draft_pending() {
        let mut committer = ASRDraftCommitter::default();

        assert_eq!(committer.update_draft("今日は"), "今日は");
        assert!(committer.has_pending_text());
        assert_eq!(
            committer.preview_latest_draft(false).as_deref(),
            Some("今日は")
        );
    }

    #[test]
    fn previews_complete_sentences_without_losing_the_tail() {
        let mut committer = ASRDraftCommitter::default();
        let draft = "こんにちは。今日は天気が";
        let _ = committer.update_draft(draft);

        assert_eq!(
            committer.preview_latest_draft(false).as_deref(),
            Some(draft)
        );
    }

    #[test]
    fn previews_multiple_sentences_with_the_incomplete_tail() {
        let mut committer = ASRDraftCommitter::default();
        let _ = committer.update_draft("あ！え？うん。まだ");

        assert_eq!(
            committer.preview_latest_draft(false).as_deref(),
            Some("あ！え？うん。まだ")
        );
    }

    #[test]
    fn supports_english_and_chinese_delimiters() {
        let mut english = ASRDraftCommitter::default();
        let _ = english.update_draft("Hello there. How are you?");
        assert_eq!(
            english.preview_latest_draft(false).as_deref(),
            Some("Hello there. How are you?")
        );

        let mut chinese = ASRDraftCommitter::default();
        let _ = chinese.update_draft("你好！今天天气不错。明天");
        assert_eq!(
            chinese.preview_latest_draft(false).as_deref(),
            Some("你好！今天天气不错。明天")
        );
    }

    #[test]
    fn maximum_wait_requires_the_configured_length() {
        let mut committer = ASRDraftCommitter::new(8);
        let _ = committer.update_draft("short");
        assert_eq!(committer.preview_latest_draft(true), None);

        let draft = "First sentence. trailing words";
        let _ = committer.update_draft(draft);
        assert_eq!(committer.preview_latest_draft(true).as_deref(), Some(draft));
    }

    #[test]
    fn punctuation_only_drafts_are_not_meaningful() {
        let mut committer = ASRDraftCommitter::default();
        let _ = committer.update_draft("……！？");

        assert!(!committer.has_pending_text());
        assert_eq!(committer.preview_latest_draft(false), None);
    }

    #[test]
    fn a_new_draft_replaces_the_previous_preview_candidate() {
        let mut committer = ASRDraftCommitter::default();
        let _ = committer.update_draft("こんにちは。");
        assert_eq!(
            committer.preview_latest_draft(false).as_deref(),
            Some("こんにちは。")
        );

        let extended = "こんにちは。まだ話しています";
        let _ = committer.update_draft(extended);
        assert_eq!(
            committer.preview_latest_draft(false).as_deref(),
            Some(extended)
        );
    }

    #[test]
    fn reset_clears_the_latest_draft() {
        let mut committer = ASRDraftCommitter::default();
        let _ = committer.update_draft("こんにちは。途中まで");
        committer.reset();

        assert!(!committer.has_pending_text());
        assert_eq!(committer.preview_latest_draft(false), None);
    }

    #[test]
    fn readable_preview_requires_corroboration_and_excludes_a_growing_tail() {
        let mut committer = ASRDraftCommitter::default();
        committer.update_draft("The synthetic check passed. The next check");
        assert_eq!(committer.preview_readable_draft(false), None);
        committer.update_draft("The synthetic check passed. The next check may");
        for allow_incomplete in [false, true] {
            assert_eq!(
                committer
                    .preview_readable_draft(allow_incomplete)
                    .as_deref(),
                Some("The synthetic check passed.")
            );
        }
        committer.update_draft("The synthetic check passed. The next check may not pass");
        assert_eq!(
            committer.preview_readable_draft(true).as_deref(),
            Some("The synthetic check passed.")
        );
        assert_eq!(
            committer.preview_latest_draft(false).as_deref(),
            Some("The synthetic check passed. The next check may not pass")
        );
        assert!(committer.has_pending_text());
    }

    #[test]
    fn late_negation_without_a_complete_sentence_waits_for_bounded_rescue() {
        let mut committer = ASRDraftCommitter::default();
        for draft in [
            "The synthetic action may",
            "The synthetic action may not",
            "The synthetic action may not proceed",
        ] {
            committer.update_draft(draft);
            assert_eq!(committer.preview_readable_draft(false), None);
            assert_eq!(
                committer.preview_readable_draft(true).as_deref(),
                Some(draft)
            );
        }
    }

    #[test]
    fn source_correction_invalidates_only_the_uncorroborated_prefix() {
        let mut committer = ASRDraftCommitter::default();
        committer.update_draft("First check passed. Second check passed. A tail");
        committer.update_draft("First check passed. Second check passed. A growing tail");
        assert_eq!(
            committer.preview_readable_draft(false).as_deref(),
            Some("First check passed. Second check passed.")
        );
        committer.update_draft("First check passed. Second check failed. A growing tail");
        assert_eq!(
            committer.preview_readable_draft(true).as_deref(),
            Some("First check passed.")
        );
        committer.update_draft("First check passed. Second check failed. A longer tail");
        assert_eq!(
            committer.preview_readable_draft(false).as_deref(),
            Some("First check passed. Second check failed.")
        );
        committer.update_draft("First check failed. Second check failed. A longer tail");
        assert_eq!(committer.preview_readable_draft(false), None);
        committer.update_draft("First check failed. Second check failed. A final tail");
        assert_eq!(
            committer.preview_readable_draft(false).as_deref(),
            Some("First check failed. Second check failed.")
        );
    }

    #[test]
    fn unicode_sentence_boundaries_keep_closing_quotes_and_exact_utf8() {
        for (first, next, expected) in [
            (
                "「合成字幕。」下一句",
                "「合成字幕。」下一句尚未完成",
                "「合成字幕。」",
            ),
            (
                "『合成字幕！』続き",
                "『合成字幕！』続きます",
                "『合成字幕！』",
            ),
            (
                "“Synthetic captions?” More",
                "“Synthetic captions?” More words",
                "“Synthetic captions?”",
            ),
            (
                "\"Synthetic captions.\" More",
                "\"Synthetic captions.\" More words",
                "\"Synthetic captions.\"",
            ),
            ("合成字幕🙂。余", "合成字幕🙂。余り", "合成字幕🙂。"),
            (
                "Is this synthetic?! More",
                "Is this synthetic?! More words",
                "Is this synthetic?!",
            ),
        ] {
            let mut committer = ASRDraftCommitter::default();
            committer.update_draft(first);
            committer.update_draft(next);
            assert_eq!(
                committer.preview_readable_draft(false).as_deref(),
                Some(expected),
                "{first}"
            );
        }
        let mut committer = ASRDraftCommitter::default();
        committer.update_draft("合成🙂。尾巴");
        committer.update_draft("合成🙃。尾巴");
        assert_eq!(committer.preview_readable_draft(false), None);
    }

    #[test]
    fn incomplete_weak_and_false_period_boundaries_never_claim_stability() {
        for draft in [
            "Synthetic clause, unfinished words",
            "Synthetic clause; unfinished words",
            "Synthetic clause: unfinished words",
            "Synthetic line\nunfinished words",
            "The synthetic price is 3.14 and rising",
            "Visit example.com/path for synthetic details",
            "Dr. Synthetic is speaking",
            "A. Synthetic is speaking",
            "The U.S. synthetic example continues",
            "The synthetic example continues, e.g. this phrase",
            "Wait... synthetic speech continues",
            "Wait… synthetic speech continues",
            "Wait…. synthetic speech continues",
            "……！？",
        ] {
            let mut committer = ASRDraftCommitter::default();
            committer.update_draft(draft);
            committer.update_draft(draft);
            assert_eq!(committer.preview_readable_draft(false), None, "{draft}");
        }
    }

    #[test]
    fn domain_extension_and_a_new_closing_quote_require_new_corroboration() {
        let mut committer = ASRDraftCommitter::default();
        committer.update_draft("Visit example.");
        committer.update_draft("Visit example.com");
        assert_eq!(committer.preview_readable_draft(false), None);
        committer.update_draft("\"Synthetic sentence.");
        committer.update_draft("\"Synthetic sentence.\" More");
        assert_eq!(committer.preview_readable_draft(false), None);
        committer.update_draft("\"Synthetic sentence.\" More words");
        assert_eq!(
            committer.preview_readable_draft(false).as_deref(),
            Some("\"Synthetic sentence.\"")
        );
    }

    #[test]
    fn reset_rearms_identical_speech_and_keeps_the_full_tail_unconsumed() {
        let mut committer = ASRDraftCommitter::default();
        let draft = "  Synthetic sentence. Unfinished tail  ";
        for _ in 0..2 {
            committer.update_draft(draft);
            assert_eq!(committer.preview_readable_draft(false), None);
            committer.update_draft(draft.trim());
            assert_eq!(
                committer.preview_readable_draft(false).as_deref(),
                Some("Synthetic sentence.")
            );
            assert_eq!(
                committer.preview_latest_draft(false).as_deref(),
                Some(draft.trim())
            );
            committer.reset();
            assert_eq!(committer.preview_readable_draft(true), None);
            assert!(!committer.has_pending_text());
        }
    }

    #[test]
    fn oversized_updates_cannot_replace_a_corroborated_boundary() {
        let mut committer = ASRDraftCommitter::default();
        committer.update_draft("Synthetic sentence. Unfinished tail");
        committer.update_draft("Synthetic sentence. A longer unfinished tail");
        committer.update_draft(&"x".repeat(crate::core::models::MAX_SUBTITLE_TEXT_BYTES + 1));
        assert_eq!(
            committer.preview_readable_draft(false).as_deref(),
            Some("Synthetic sentence.")
        );
    }

    #[test]
    fn started_prefix_allows_tail_rescue_without_reverting_to_an_older_boundary() {
        let mut committer = ASRDraftCommitter::default();
        let first = "Synthetic check passed.";
        committer.update_draft("Synthetic check passed. The next check grows");
        committer.update_draft("Synthetic check passed. The next check grows longer");
        assert_eq!(
            committer.preview_readable_draft(false).as_deref(),
            Some(first)
        );
        assert!(committer.note_preview_started(first));
        committer.update_draft("Synthetic check passed. The next check grows longer still");
        assert_eq!(committer.preview_readable_draft(false), None);
        let rescued = committer.preview_readable_draft(true).unwrap();
        assert_eq!(
            rescued,
            "Synthetic check passed. The next check grows longer still"
        );
        assert!(committer.note_preview_started(&rescued));
        assert!(committer.note_preview_started(first));
        assert_eq!(committer.previewed_end, rescued.len());
        assert_eq!(committer.preview_readable_draft(false), None);
        committer.update_draft("Synthetic check passed. The next check grows longer still.");
        assert_eq!(committer.preview_readable_draft(false), None);
        committer.update_draft("Synthetic check passed. The next check grows longer still. A tail");
        assert_eq!(
            committer.preview_readable_draft(false).as_deref(),
            Some("Synthetic check passed. The next check grows longer still.")
        );
    }

    #[test]
    fn admitted_progress_shrinks_at_a_correction_and_rejects_obsolete_candidates() {
        let mut committer = ASRDraftCommitter::default();
        committer.update_draft("Synthetic check passed. A tail");
        committer.update_draft("Synthetic check passed. A longer tail");
        assert!(committer.note_preview_started("Synthetic check passed."));
        committer.update_draft("Synthetic check failed. A longer tail");
        let progress = committer.previewed_end;
        assert_eq!(progress, "Synthetic check ".len());
        assert_eq!(committer.preview_readable_draft(false), None);
        assert!(!committer.note_preview_started("Synthetic check passed."));
        assert!(!committer.note_preview_started(""));
        assert_eq!(committer.previewed_end, progress);
        committer.update_draft("Synthetic check failed. A final tail");
        assert_eq!(
            committer.preview_readable_draft(false).as_deref(),
            Some("Synthetic check failed.")
        );
    }

    #[test]
    fn a_utf8_interior_progress_offset_is_never_used_for_slicing() {
        let mut committer = ASRDraftCommitter::default();
        committer.update_draft("合成🙂。尾巴");
        committer.update_draft("合成🙂。尾巴增长");
        assert!(committer.note_preview_started("合成🙂。"));
        committer.update_draft("合成🙃。尾巴增长");
        assert!(!committer
            .latest_draft
            .is_char_boundary(committer.previewed_end));
        assert_eq!(committer.preview_readable_draft(false), None);
        assert_eq!(
            committer.preview_readable_draft(true).as_deref(),
            Some("合成🙃。尾巴增长")
        );
        committer.update_draft("合成🙃。尾巴继续增长");
        assert_eq!(
            committer.preview_readable_draft(false).as_deref(),
            Some("合成🙃。")
        );
    }

    #[test]
    fn reset_clears_admitted_progress_for_repeated_speech() {
        let mut committer = ASRDraftCommitter::default();
        for _ in 0..2 {
            committer.update_draft("Synthetic sentence. A tail");
            assert_eq!(committer.preview_readable_draft(false), None);
            committer.update_draft("Synthetic sentence. A longer tail");
            assert_eq!(
                committer.preview_readable_draft(false).as_deref(),
                Some("Synthetic sentence.")
            );
            assert!(committer.note_preview_started("Synthetic sentence."));
            assert_eq!(committer.preview_readable_draft(false), None);
            committer.reset();
            assert_eq!(committer.previewed_end, 0);
            assert_eq!(committer.preview_readable_draft(true), None);
        }
    }

    #[test]
    fn a_started_complete_prefix_rejects_decimal_and_domain_extensions() {
        for (complete, extension) in [
            (
                "The synthetic amount is 3.",
                "The synthetic amount is 3.14 dollars",
            ),
            ("Visit example.", "Visit example.com for synthetic details"),
            (
                "First sentence. The amount is 3.",
                "First sentence. The amount is 3.14 dollars",
            ),
        ] {
            let mut committer = ASRDraftCommitter::default();
            committer.update_draft(complete);
            committer.update_draft(complete);
            assert!(committer.retains_complete_prefix(complete));
            assert!(committer.note_preview_started(complete));
            committer.update_draft(extension);
            assert!(committer.latest_draft.starts_with(complete));
            assert!(!committer.retains_complete_prefix(complete));
        }
    }

    #[test]
    fn complete_prefix_retention_is_independent_of_progress_and_rejects_source_corrections() {
        for (complete, extension) in [
            ("Synthetic sentence.", "Synthetic sentence. More words"),
            ("「合成字幕。」", "「合成字幕。」下一句"),
            ("Synthetic sentence?!", "Synthetic sentence?! More words"),
        ] {
            let mut committer = ASRDraftCommitter::default();
            committer.update_draft(complete);
            assert!(committer.retains_complete_prefix(complete));
            assert!(committer.note_preview_started(complete));
            committer.update_draft(extension);
            assert!(committer.retains_complete_prefix(complete));
            committer.update_draft("A corrected synthetic sentence. More words");
            assert!(!committer.retains_complete_prefix(complete));
        }
        let mut committer = ASRDraftCommitter::default();
        committer.update_draft("\"Synthetic sentence.\" More words");
        assert!(!committer.retains_complete_prefix("\"Synthetic sentence."));
        assert!(committer.retains_complete_prefix("\"Synthetic sentence.\""));
        assert!(!committer.retains_complete_prefix(""));
        assert!(!committer.retains_complete_prefix("\"Synthetic sentence"));
    }

    #[test]
    fn maximum_sized_punctuation_and_closer_runs_keep_valid_bounded_offsets() {
        let limit = crate::core::models::MAX_SUBTITLE_TEXT_BYTES;
        let punctuation = "?".repeat(limit / 2);
        let draft = format!("{punctuation}{}", "\"".repeat(limit - punctuation.len()));
        let mut committer = ASRDraftCommitter::default();
        committer.update_draft(&draft);
        committer.update_draft(&draft);
        assert_eq!(committer.stable_prefix_end, limit);
        assert_eq!(committer.preview_readable_draft(false), None);
        let changed = format!("{}”", &draft[..limit - '”'.len_utf8()]);
        committer.update_draft(&changed);
        assert_eq!(committer.stable_prefix_end, 0);
        assert_eq!(committer.preview_readable_draft(false), None);
        assert_eq!(committer.preview_latest_draft(false), None);
    }
}
