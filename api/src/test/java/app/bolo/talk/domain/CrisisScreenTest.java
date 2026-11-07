package app.bolo.talk.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CrisisScreenTest {

    @Test
    void flagsCrisisWordsInEnglishAndBanglaTransliteration() {
        assertThat(CrisisScreen.mayBeCrisis("Sometimes I want to kill myself")).isTrue();
        assertThat(CrisisScreen.mayBeCrisis("I think about suicide")).isTrue();
        assertThat(CrisisScreen.mayBeCrisis("ami morte chai")).isTrue();
        assertThat(CrisisScreen.mayBeCrisis("I want to die")).isTrue();
    }

    @Test
    void ordinaryTalkIsNotFlagged() {
        assertThat(CrisisScreen.mayBeCrisis("The deadline is killing me, haha")).isFalse();
        assertThat(CrisisScreen.mayBeCrisis("I hurt my leg playing cricket")).isFalse();
    }

    @Test
    void theScriptNamesLocalHelp() {
        assertThat(CrisisScript.HELP).extracting(CrisisScript.Help::number).contains("999", "09612-119911");
        assertThat(String.join(" ", CrisisScript.LINES)).doesNotContainIgnoringCase("practice");
    }
}
