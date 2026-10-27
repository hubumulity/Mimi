//! Degenerate utterances: screams, gasps, and other vocalizations that speech
//! recognition renders as a run of filler sounds with no lexical content.
//!
//! A text is degenerate only when, after punctuation, whitespace, and
//! prolongation marks are stripped, it consists entirely of filler material
//! and reaches a minimum size. A single filler, a pair of fillers, and fillers
//! inside a real sentence are all kept: this filter targets continuous filler
//! runs, not ordinary interjections. It is language-agnostic by design, so the
//! reducer can apply it without knowing the session's source language.

/// A filler run shorter than this is kept. A lone `啊` can be an answer and a
/// lone `oh` can be an acknowledgement; only a continuous run reads as a
/// scream or a gasp.
const MIN_FILLER_UNITS: usize = 3;

/// Filler characters for unspaced scripts (Chinese, Japanese kana, Korean).
/// Laughter (`哈`) and meaningful snorts (`哼`) are deliberately excluded: a
/// whole utterance of laughter keeps its meaning, while screams and gasps
/// surface as 嗯/啊/哦 runs.
const FILLER_CHARS: &[char] = &[
    // Chinese vocalizations: moans, gasps, screams.
    '嗯', '啊', '哦', '呃', '额', '唔', '吁', '呼', '呀', '哇',
    // Japanese vocalizations.
    'あ', 'ア', 'う', 'ウ', 'え', 'エ', 'お', 'オ', 'ん', 'ン', // Korean vocalizations.
    '아', '어', '음', '으',
];

/// Filler words for spaced scripts, matched whole-word and case-insensitively.
const FILLER_WORDS: &[&str] = &[
    "ah", "uh", "um", "hmm", "oh", "ooh", "aah", "ugh", "mm", "mmm", "eh",
];

/// Marks that stretch a filler without adding content. They are dropped before
/// the check, so `あーあーあー` reads as `あああ`. The chōonpu is a modifier
/// letter, so it needs an explicit entry instead of relying on punctuation.
const PROLONGATION: &[char] = &['—', '–', '～', '〜', 'ー', 'ｰ'];

/// Returns true when `text` is nothing but a continuous filler run: a scream,
/// a gasp, or a moan transcribed as repeated interjections.
pub fn is_degenerate_utterance(text: &str) -> bool {
    // Replace every non-alphanumeric character with a space, so punctuation
    // can neither join tokens nor hide inside a character run.
    let normalized: String = text
        .chars()
        .map(|c| {
            if c.is_alphanumeric() && !PROLONGATION.contains(&c) {
                c
            } else {
                ' '
            }
        })
        .collect();
    // Character mode: unspaced scripts read filler runs character by character.
    let chars: Vec<char> = normalized.chars().filter(|c| !c.is_whitespace()).collect();
    if chars.len() >= MIN_FILLER_UNITS && chars.iter().all(|c| FILLER_CHARS.contains(c)) {
        return true;
    }
    // Word mode: spaced scripts read filler runs word by word.
    let tokens: Vec<&str> = normalized.split_whitespace().collect();
    tokens.len() >= MIN_FILLER_UNITS
        && tokens
            .iter()
            .all(|token| FILLER_WORDS.contains(&token.to_lowercase().as_str()))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn chinese_filler_runs_are_degenerate() {
        assert!(is_degenerate_utterance("啊啊啊"));
        assert!(is_degenerate_utterance("嗯嗯嗯嗯"));
        assert!(is_degenerate_utterance("啊啊啊！"));
        assert!(is_degenerate_utterance("啊——啊——啊"));
        assert!(is_degenerate_utterance("嗯，啊，哦"));
    }

    #[test]
    fn short_or_embedded_chinese_fillers_are_kept() {
        assert!(!is_degenerate_utterance("啊"));
        assert!(!is_degenerate_utterance("啊啊"));
        assert!(!is_degenerate_utterance("嗯，我觉得"));
        assert!(!is_degenerate_utterance("就是这样，啊"));
        assert!(!is_degenerate_utterance("啊啊啊救命"));
        assert!(!is_degenerate_utterance("哈哈哈"));
    }

    #[test]
    fn japanese_and_korean_filler_runs_are_degenerate() {
        assert!(is_degenerate_utterance("あああ"));
        assert!(is_degenerate_utterance("あーあーあー"));
        assert!(!is_degenerate_utterance("ありがとう"));
        assert!(is_degenerate_utterance("아아아"));
        assert!(!is_degenerate_utterance("아이고"));
    }

    #[test]
    fn english_filler_runs_are_degenerate_word_by_word() {
        assert!(is_degenerate_utterance("oh oh oh"));
        assert!(is_degenerate_utterance("Oh, oh, oh!"));
        assert!(is_degenerate_utterance("um um um"));
        assert!(!is_degenerate_utterance("oh"));
        assert!(!is_degenerate_utterance("oh oh"));
        assert!(!is_degenerate_utterance("oh I see"));
        assert!(!is_degenerate_utterance("um, let me think"));
    }

    #[test]
    fn empty_and_non_filler_text_is_kept() {
        assert!(!is_degenerate_utterance(""));
        assert!(!is_degenerate_utterance("   "));
        assert!(!is_degenerate_utterance("。。。"));
        assert!(!is_degenerate_utterance("今天天气很好"));
        assert!(!is_degenerate_utterance("111"));
    }
}
