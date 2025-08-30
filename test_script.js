// 전역 변수 사용 (나쁜 예)
var globalVariable = "test";

// 하드코딩된 값들
const API_KEY = "sk-1234567890abcdef";
const PASSWORD = "123456";

// 콜백 지옥 예제
function callbackHell() {
    fetch('/api/data', function (response) {
        response.json().then(function (data) {
            data.forEach(function (item) {
                fetch('/api/process', function (processResponse) {
                    processResponse.json().then(function (result) {
                        console.log(result);
                        fetch('/api/save', function (saveResponse) {
                            saveResponse.json().then(function (saveResult) {
                                console.log('완료!');
                            });
                        });
                    });
                });
            });
        });
    });
}

// 보안 취약점이 있는 함수
function vulnerableFunction(userInput) {
    // XSS 취약점
    document.getElementById('output').innerHTML = userInput;

    // eval 사용 (위험)
    eval(userInput);

    // SQL 인젝션 취약점
    const query = `SELECT * FROM users WHERE name = '${userInput}'`;

    return query;
}

// 비효율적인 함수
function inefficientFunction() {
    // 불필요한 반복문
    const array = [1, 2, 3, 4, 5];
    let result = [];

    for (let i = 0; i < array.length; i++) {
        result.push(array[i] * 2);
    }

    // 중복 계산
    const sum1 = array.reduce((a, b) => a + b, 0);
    const sum2 = array.reduce((a, b) => a + b, 0);

    return { result, sum1, sum2 };
}

// 나쁜 클래스 설계
class BadClass {
    constructor() {
        this.data = [];
        this.counter = 0;
    }

    // 타입 체크 없음
    addItem(item) {
        this.data.push(item);
        this.counter++;
    }

    // 참조 노출
    getData() {
        return this.data;
    }

    // 긴 함수 (단일 책임 원칙 위반)
    processData() {
        const processed = [];

        for (let i = 0; i < this.data.length; i++) {
            const item = this.data[i];

            if (typeof item === 'string') {
                processed.push(item.toUpperCase());
            } else if (typeof item === 'number') {
                processed.push(item * 2);
            } else {
                processed.push(String(item));
            }
        }

        // 중복 로직
        const result = [];
        for (let i = 0; i < processed.length; i++) {
            result.push(processed[i]);
        }

        return result;
    }
}

// 예외 처리 없는 함수
function noErrorHandling() {
    const data = JSON.parse(localStorage.getItem('userData'));
    const user = data.user;
    const name = user.name;

    return name;
}

// 메모리 누수 가능성
function potentialMemoryLeak() {
    const elements = document.querySelectorAll('.item');

    elements.forEach(element => {
        element.addEventListener('click', function () {
            console.log('클릭됨!');
        });
    });
}

// 사용되지 않는 변수
const unusedVariable = "사용되지 않음";

// 메인 실행
document.addEventListener('DOMContentLoaded', function () {
    console.log(globalVariable);

    // 문제가 있는 함수들 호출
    callbackHell();
    vulnerableFunction('<script>alert("XSS")</script>');
    inefficientFunction();

    const obj = new BadClass();
    obj.addItem("test");
    obj.addItem(123);
    obj.processData();

    noErrorHandling();
    potentialMemoryLeak();
});
