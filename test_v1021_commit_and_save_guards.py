from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent


def strip_kt_comments(text: str) -> str:
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    return re.sub(r"//[^\n]*", "", text)


def test_transaction_manager_has_explicit_commit_helper_and_no_postcommit_rollback():
    tx = strip_kt_comments(
        (ROOT / "app/src/main/java/com/zaba/zcode/core/packageengine/TransactionManager.kt").read_text()
    )
    boundary = strip_kt_comments(
        (ROOT / "app/src/main/java/com/zaba/zcode/core/packageengine/ActivationCommitBoundary.kt").read_text()
    )
    assert "ActivationCommitBoundary.promoteAndCommit" in tx
    commit = tx.index("ActivationCommitBoundary.promoteAndCommit")
    post = tx.index("val postCommitSteps", commit)
    result = tx.index("return ActivationResult(", post)
    post_block = tx[post:result]
    assert "deleteRecursively()" in post_block
    assert "committed.isFailure" not in post_block
    assert "oldEnvironmentPreserved = false" in tx[result:]
    assert "commitState()" in boundary
    assert boundary.index("commitState()") < boundary.index("Result.success(Unit)")
    assert "promoted.forEach" in boundary


def test_postcommit_callbacks_are_best_effort_and_runtime_fault_tests_exist():
    tx = strip_kt_comments(
        (ROOT / "app/src/main/java/com/zaba/zcode/core/packageengine/TransactionManager.kt").read_text()
    )
    test = (ROOT / "app/src/test/java/com/zaba/zcode/core/packageengine/ActivationCommitBoundaryTest.kt").read_text()
    assert "ActivationCommitBoundary.runBestEffort(postCommitSteps)" in tx
    assert "postCommitCallbackFailureCannotDeleteCommittedGeneration" in test
    assert "commitFailureDeletesOnlyNewGenerationsAndLeavesOldActivePath" in test
    assert "injected log failure" in test
    assert "injected state failure" in test


def test_inactive_save_recheck_and_actual_write_share_one_gate():
    vm = strip_kt_comments(
        (ROOT / "app/src/main/java/com/zaba/zcode/WorkspaceViewModel.kt").read_text()
    )
    start = vm.index("fun updateCodeForFile(")
    end = vm.index("fun createNewFile(", start)
    block = vm[start:end]
    assert "QueuedDocumentSave" in vm
    assert "workspaceMutations.writeIfCurrent(" in block
    write_gate = block[block.index("workspaceMutations.writeIfCurrent("):]
    assert "documentRevisions[queued.documentId] == queued.revision" in write_gate
    assert "fileDrafts[queued.documentId] == queued.code" in write_gate
    assert "FileManager.saveFile(filesDir, queued.documentId, queued.code)" in write_gate
    assert write_gate.index("documentRevisions[queued.documentId] == queued.revision") < write_gate.index("FileManager.saveFile")


def test_clear_delete_rename_close_use_same_gate_and_barrier_test_exists():
    vm = strip_kt_comments(
        (ROOT / "app/src/main/java/com/zaba/zcode/WorkspaceViewModel.kt").read_text()
    )
    for start_marker, end_marker in (
        ("fun closeFile(", "fun renameFile("),
        ("fun renameFile(", "fun deleteFile("),
        ("fun deleteFile(", "fun getAllFiles("),
        ("fun clearAllDrafts(", "fun restoreLastClear("),
    ):
        block = vm[vm.index(start_marker):vm.index(end_marker, vm.index(start_marker))]
        assert "workspaceMutations.mutate" in block, f"{start_marker} does not own mutation gate"
    test = (ROOT / "app/src/test/java/com/zaba/zcode/core/files/WorkspaceMutationGateTest.kt").read_text()
    assert "CountDownLatch" in test
    assert "Thread.sleep" not in test
    assert "clearWaitsForAcceptedWriteThenDeletesItSoFileCannotResurrect" in test
    assert "saveQueuedBeforeClearBecomesNoOpWhenClearCommitsFirst" in test
