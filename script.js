let display = document.getElementById('display');
let currentInput = '0';
let firstValue = null;
let operator = null;
let waitingForSecondValue = false;

function updateDisplay() {
  display.textContent = currentInput;
}

function inputDigit(digit) {
  if (waitingForSecondValue) {
    currentInput = digit;
    waitingForSecondValue = false;
  } else {
    currentInput = currentInput === '0' ? digit : currentInput + digit;
  }
  updateDisplay();
}

function inputDecimal() {
  if (waitingForSecondValue) {
    currentInput = '0.';
    waitingForSecondValue = false;
    updateDisplay();
    return;
  }

  if (!currentInput.includes('.')) {
    currentInput += '.';
    updateDisplay();
  }
}

function handleOperator(nextOperator) {
  const inputValue = Number(currentInput);

  if (operator && waitingForSecondValue) {
    operator = nextOperator;
    return;
  }

  if (firstValue === null) {
    firstValue = inputValue;
  } else if (operator) {
    const result = calculate(firstValue, inputValue, operator);
    currentInput = String(result);
    firstValue = result;
  }

  waitingForSecondValue = true;
  operator = nextOperator;
  updateDisplay();
}

function calculate(first, second, op) {
  switch (op) {
    case '+':
      return first + second;
    case '-':
      return first - second;
    case '*':
      return first * second;
    case '/':
      return second === 0 ? 'Error' : first / second;
    default:
      return second;
  }
}

function clearAll() {
  currentInput = '0';
  firstValue = null;
  operator = null;
  waitingForSecondValue = false;
  updateDisplay();
}

function deleteLast() {
  if (waitingForSecondValue) return;

  currentInput = currentInput.length > 1 ? currentInput.slice(0, -1) : '0';
  updateDisplay();
}

function evaluate() {
  if (operator === null || waitingForSecondValue) return;

  const inputValue = Number(currentInput);
  const result = calculate(firstValue, inputValue, operator);

  currentInput = String(result);
  firstValue = null;
  operator = null;
  waitingForSecondValue = false;
  updateDisplay();
}

const buttons = document.querySelectorAll('.btn');

buttons.forEach((button) => {
  button.addEventListener('click', () => {
    const value = button.dataset.value;

    if (/[0-9]/.test(value)) {
      inputDigit(value);
      return;
    }

    if (value === '.') {
      inputDecimal();
      return;
    }

    if (['+', '-', '*', '/'].includes(value)) {
      handleOperator(value);
      return;
    }

    if (value === '=') {
      evaluate();
      return;
    }

    if (value === 'C') {
      clearAll();
      return;
    }

    if (value === 'DEL') {
      deleteLast();
    }
  });
});

document.addEventListener('keydown', (event) => {
  const { key } = event;

  if (/[0-9]/.test(key)) {
    inputDigit(key);
    return;
  }

  if (key === '.') {
    inputDecimal();
    return;
  }

  if (['+', '-', '*', '/'].includes(key)) {
    handleOperator(key);
    return;
  }

  if (key === 'Enter' || key === '=') {
    evaluate();
    return;
  }

  if (key === 'Backspace') {
    deleteLast();
    return;
  }

  if (key.toLowerCase() === 'c') {
    clearAll();
  }
});

updateDisplay();
