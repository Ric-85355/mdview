# Makefile — created 2026-08-26, version 0.3.0.
# Purpose: build mdview-c and its curses-independent parity tests.
# Algorithm: compile shared model objects once and link the TUI with ncursesw.

CC ?= cc
CPPFLAGS += -D_DEFAULT_SOURCE -D_XOPEN_SOURCE=600 -Ic-src
CFLAGS ?= -O2
CFLAGS += -std=c11 -Wall -Wextra -Wpedantic
NCURSESW_CFLAGS := $(shell pkg-config --cflags-only-I ncursesw)
NCURSESW_LIBS := $(shell pkg-config --libs ncursesw)

BUILD_DIR := build
MODEL_OBJECT := $(BUILD_DIR)/mdview_model.o
TUI_OBJECT := $(BUILD_DIR)/mdview_c.o
TEST_OBJECT := $(BUILD_DIR)/test_mdview_model.o
DUMP_OBJECT := $(BUILD_DIR)/dump_mdview_model.o
NAVIGATION_TEST_OBJECT := $(BUILD_DIR)/test_mdview_navigation.o

.PHONY: all clean test

all: $(BUILD_DIR)/mdview-c

$(BUILD_DIR):
	mkdir -p $(BUILD_DIR)

$(MODEL_OBJECT): c-src/mdview_model.c c-src/mdview_model.h | $(BUILD_DIR)
	$(CC) $(CPPFLAGS) $(CFLAGS) $(NCURSESW_CFLAGS) -c $< -o $@

$(TUI_OBJECT): c-src/mdview_c.c c-src/mdview_model.h | $(BUILD_DIR)
	$(CC) $(CPPFLAGS) $(CFLAGS) $(NCURSESW_CFLAGS) -c $< -o $@

$(TEST_OBJECT): tests/c/test_mdview_model.c c-src/mdview_model.h | $(BUILD_DIR)
	$(CC) $(CPPFLAGS) $(CFLAGS) -c $< -o $@

$(DUMP_OBJECT): tests/c/dump_mdview_model.c c-src/mdview_model.h | $(BUILD_DIR)
	$(CC) $(CPPFLAGS) $(CFLAGS) -c $< -o $@

$(NAVIGATION_TEST_OBJECT): tests/c/test_mdview_navigation.c c-src/mdview_c.c c-src/mdview_model.h | $(BUILD_DIR)
	$(CC) $(CPPFLAGS) $(CFLAGS) $(NCURSESW_CFLAGS) -c $< -o $@

$(BUILD_DIR)/mdview-c: $(MODEL_OBJECT) $(TUI_OBJECT)
	$(CC) $(CFLAGS) $^ $(NCURSESW_LIBS) -o $@

$(BUILD_DIR)/test-mdview-model: $(MODEL_OBJECT) $(TEST_OBJECT)
	$(CC) $(CFLAGS) $^ -o $@

$(BUILD_DIR)/dump-mdview-model: $(MODEL_OBJECT) $(DUMP_OBJECT)
	$(CC) $(CFLAGS) $^ -o $@

$(BUILD_DIR)/test-mdview-navigation: $(MODEL_OBJECT) $(NAVIGATION_TEST_OBJECT)
	$(CC) $(CFLAGS) $^ $(NCURSESW_LIBS) -o $@

test: $(BUILD_DIR)/mdview-c $(BUILD_DIR)/test-mdview-model $(BUILD_DIR)/dump-mdview-model $(BUILD_DIR)/test-mdview-navigation
	./$(BUILD_DIR)/test-mdview-model
	./$(BUILD_DIR)/test-mdview-navigation
	sh tests/c/test_cli.sh ./$(BUILD_DIR)/mdview-c
	python3 tests/compare_models.py ./mdview ./$(BUILD_DIR)/dump-mdview-model tests/fixtures/parity.md
	python3 tests/pty_smoke.py ./$(BUILD_DIR)/mdview-c tests/fixtures/parity.md

clean:
	rm -f $(BUILD_DIR)/mdview-c $(BUILD_DIR)/test-mdview-model \
		$(BUILD_DIR)/dump-mdview-model $(BUILD_DIR)/test-mdview-navigation \
		$(BUILD_DIR)/*.o
